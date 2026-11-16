package com.openapi.ks.myapp.avatar

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * v1 形象：静态立绘 + 表情图 + 两帧嘴型 + 眨眼，不依赖第三方运行时（无授权风险）。
 *
 * 它不是 Composable，也不是动画播放器：只把状态放进 StateFlow，由 [SimpleAvatar]
 * 画出来。呼吸的插值完全在 Composable 里按 [state] 决定快慢——渲染器只给状态，
 * 这样状态可以脱离 Compose 测试，换渲染器时上层也不用动。
 */
class SimpleAvatarRenderer : AvatarRenderer {

    override val capabilities: Set<AvatarCapability> =
        setOf(AvatarCapability.STATE, AvatarCapability.EMOTION, AvatarCapability.MOUTH, AvatarCapability.TOUCH_TAP)

    // 不用 Dispatchers.Main：StateFlow 允许任意线程写入，Compose 会在主线程收集；
    // 用 Default 还省掉了纯 JVM 测试里必须装 Main dispatcher 的麻烦。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var blinkJob: Job? = null

    // 眨眼循环跑在 Default 上，release() 在主线程——没有 @Volatile 的话循环可能
    // 一直读到旧值。（scope.cancel() 也能兜住，但靠异常退出不是本意。）
    @Volatile
    private var released = false

    private val _state = MutableStateFlow(AvatarState.IDLE)
    val state: StateFlow<AvatarState> = _state.asStateFlow()

    private val _spec = MutableStateFlow<AvatarSpec?>(null)
    val spec: StateFlow<AvatarSpec?> = _spec.asStateFlow()

    /** 当前表情图；null 表示用立绘。 */
    private val _expressionRes = MutableStateFlow<Int?>(null)
    val expressionRes: StateFlow<Int?> = _expressionRes.asStateFlow()

    /** 当前表情图（下载形象用）；null 表示用立绘。 */
    private val _expressionFile = MutableStateFlow<java.io.File?>(null)
    val expressionFile: StateFlow<java.io.File?> = _expressionFile.asStateFlow()

    private val _mouthOpen = MutableStateFlow(false)
    val mouthOpen: StateFlow<Boolean> = _mouthOpen.asStateFlow()

    private val _eyeOpen = MutableStateFlow(true)
    val eyeOpen: StateFlow<Boolean> = _eyeOpen.asStateFlow()

    /**
     * 被点了几次。界面拿它当 key 播一次「被点到」的反馈动画；
     * 用自增计数而不是布尔值，连点两下也能各播一次（布尔值会在第二下看出没变化）。
     */
    private val _tapCount = MutableStateFlow(0L)
    val tapCount: StateFlow<Long> = _tapCount.asStateFlow()

    /** 最近一次点中的部位（[AvatarRegion]），供界面/埋点使用。 */
    private val _lastTappedRegion = MutableStateFlow<String?>(null)
    val lastTappedRegion: StateFlow<String?> = _lastTappedRegion.asStateFlow()

    override fun bind(spec: AvatarSpec) {
        if (released) return
        _spec.value = spec
        _expressionRes.value = null
        _expressionFile.value = null
        _mouthOpen.value = false
        _eyeOpen.value = true
        _state.value = AvatarState.IDLE
        startBlinking()
    }

    override fun dispatch(command: AvatarCommand) {
        if (released) return
        // 与 capabilities 声明保持一致：不支持的指令静默丢弃，不记日志也不崩。
        if (!supports(command)) return

        when (command) {
            is AvatarCommand.SetState -> {
                _state.value = command.state
                // 只有说话时嘴才动；其余状态强制闭合，免得上一段音频的残留嘴型卡住。
                if (command.state != AvatarState.SPEAKING) _mouthOpen.value = false
            }

            is AvatarCommand.Emotion -> {
                // 没映射到表情就保持原样：角色卡里写了本渲染器没做的情绪是正常事，
                // 这也意味着不必为此记日志。
                _spec.value?.expressions?.get(command.name)?.let { _expressionRes.value = it }
                _spec.value?.files?.expressions?.get(command.name)?.let { _expressionFile.value = it }
            }

            is AvatarCommand.Mouth -> {
                if (_state.value != AvatarState.SPEAKING) return
                // NaN / Infinity 会污染比较结果，直接丢；其余压到合法区间。
                if (!command.openness.isFinite()) return
                _mouthOpen.value = command.openness.coerceIn(0f, 1f) >= MOUTH_THRESHOLD
            }

            is AvatarCommand.Gaze, is AvatarCommand.Gesture -> Unit
        }
    }

    override fun release() {
        released = true
        blinkJob?.cancel()
        blinkJob = null
        scope.cancel()
    }

    /**
     * 点击判定与反馈。
     *
     * 只处理 TAP：分层立绘没有骨架，命中的是「头部矩形」这种粗略区域
     * （设计文档 §2.5 的降级方案）。坐标是形象区域内的归一化值，y 向下。
     *
     * 为什么在渲染器里判而不是在 Composable 里：换渲染器（Spine / Live2D）后
     * 命中方式完全不同（包围盒 / 槽位），但「点到了什么部位」这个结论要一致，
     * 所以判定权归渲染器，界面只负责把手势转成 [AvatarTouch]。
     */
    override fun onTouch(event: AvatarTouch): TouchResult {
        if (released) return TouchResult.Ignored
        if (event.kind != AvatarTouchKind.TAP) return TouchResult.Ignored
        if (AvatarCapability.TOUCH_TAP !in capabilities) return TouchResult.Ignored
        // 素材还没 bind（或已缺席）时没什么可点的：返回 Ignored，界面不会播反馈。
        if (_spec.value == null) return TouchResult.Ignored
        val region = hitTest(event.x, event.y)
        _lastTappedRegion.value = region
        _tapCount.value += 1
        return TouchResult.Handled(region)
    }

    /** 归一化坐标 → 部位。头部占上方 42%（立绘多半是胸像或大头），其余算身体。 */
    private fun hitTest(x: Float, y: Float): String {
        if (x !in 0f..1f || y !in 0f..1f) return AvatarRegion.BODY
        return if (y <= HEAD_BAND) AvatarRegion.HEAD else AvatarRegion.BODY
    }

    private fun supports(command: AvatarCommand): Boolean = when (command) {
        is AvatarCommand.SetState -> AvatarCapability.STATE in capabilities
        is AvatarCommand.Emotion -> AvatarCapability.EMOTION in capabilities
        is AvatarCommand.Mouth -> AvatarCapability.MOUTH in capabilities
        is AvatarCommand.Gaze -> AvatarCapability.GAZE in capabilities
        is AvatarCommand.Gesture -> AvatarCapability.GESTURE in capabilities
    }

    private fun startBlinking() {
        blinkJob?.cancel()
        blinkJob = scope.launch {
            while (!released) {
                // 间隔随机，否则眨眼会变成机械节拍。
                delay(Random.nextLong(BLINK_MIN_MS, BLINK_MAX_MS))
                if (released) return@launch
                _eyeOpen.value = false
                delay(BLINK_HOLD_MS)
                _eyeOpen.value = true
            }
        }
    }

    private companion object {
        /** 低于这个振幅就当作闭嘴，滤掉静音段与音轨暂停时的抖动。 */
        const val MOUTH_THRESHOLD = 0.35f
        const val BLINK_MIN_MS = 3_000L
        const val BLINK_MAX_MS = 6_000L
        const val BLINK_HOLD_MS = 120L

        /**
         * 头部在形象区域里的下边界（归一化，y 向下）。42% 是胸像构图的估计值：
         * 现在两套素材（内置、下载包）都是大头半身像，改动前应先在真机上比对。
         */
        const val HEAD_BAND = 0.42f
    }
}
