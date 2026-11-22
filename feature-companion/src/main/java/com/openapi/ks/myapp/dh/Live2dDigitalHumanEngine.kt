package com.openapi.ks.myapp.dh

import android.content.Context
import android.util.Log
import android.view.View
import com.openapi.ks.dh.Live2dRuntime
import com.openapi.ks.myapp.avatar.AvatarCapability
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.AvatarTouchKind
import com.openapi.ks.myapp.avatar.TouchResult

/**
 * Live2D 适配器：直接调 `:dh-engines` 的 [Live2dRuntime]（已内置 Java/JNI 包装，无反射）。
 * 素材没下好（LIVE2D 清单仍空，或 cache 里还没有模型）时 [availability] 回「未下载」并标
 * [DhEngineAvailability.needsDownload]；JNI 包装库缺失时回「native 库缺失」并 blocked。
 * 口型走 [Live2dRuntime.setMouth]，情绪走 [Live2dRuntime.setExpression]。
 *
 * 触点互动（demo #2）：[onTouch] 吃舞台层认出的轻触（归一化坐标），用
 * [Live2dTouchReaction] 纯状态机决定表情名——点头切换脸红、点身体循环换装——
 * 再经 `nativeApplyExpression` 交给 native 交叉淡入淡出。表情名对应的 exp3 资产
 * 由 lab 清单注册（scripts/dh-lab/live2d-lab-assets/，seed-assets.sh live2d 推送）。
 */
class Live2dDigitalHumanEngine(
    private val modelDirName: String = Live2dRuntime.DEFAULT_MODEL_DIR,
    private val modelJsonName: String = "$modelDirName.model3.json",
) : DigitalHumanEngine {

    override val id: DhEngineId = DhEngineId.LIVE2D

    override val capabilities: Set<AvatarCapability> = setOf(
        AvatarCapability.MOUTH,
        AvatarCapability.EMOTION,
        AvatarCapability.GAZE,
        AvatarCapability.STATE,
        AvatarCapability.TOUCH_TAP,
    )

    private var runtime: Live2dRuntime? = null
    private var viewCreated = false
    private var released = false

    /** 触点互动状态（(服装,脸红,kani,凉鞋)）；release 后重建引擎会回到初始态——与画面一致。 */
    private var reaction = Live2dTouchReactionState()

    /**
     * 主题色在 buildGlView 之前也可能先到（通话页先组合主题、后挂 View），
     * 存一份在 createView 成功后补推；native 侧每帧读，晚一帧无感。
     */
    private var pendingBackgroundArgb: Int? = null

    override fun setBackground(argb: Int) {
        pendingBackgroundArgb = argb
        runtime?.takeIf { viewCreated }?.let { rt ->
            val (r, g, b) = argbToNormRgb(argb)
            rt.setBackground(r, g, b)
        }
    }

    override fun bind(spec: AvatarSpec) {
        // Lab 默认模型；后续可把 AvatarSpec.assetKey 映射到目录名。
    }

    override fun dispatch(command: AvatarCommand) {
        val rt = runtime?.takeIf { viewCreated }
        if (command is AvatarCommand.Mouth) traceMouth(command.openness)
        rt ?: return
        when (command) {
            is AvatarCommand.Mouth -> {
                rt.setMouth(command.openness)
                // 说话时头轻轻摆动（慢速两轴正弦）；闭口时回正。让「头会动」肉眼可见。
                val (gx, gy) = Live2dHeadSway.gaze(command.openness, System.currentTimeMillis())
                rt.setGaze(gx, gy)
            }
            is AvatarCommand.Gaze -> rt.setGaze(command.x, command.y)
            is AvatarCommand.Emotion -> rt.setExpression(command.name)
            else -> Unit
        }
    }

    /** 口型轨迹的节流状态；[clockMs] 测试注入门（假时钟让日志轨迹可断言）。 */
    private var mouthLastLogMs = -1L
    private var mouthWasOpen = false
    internal var clockMs: () -> Long = System::currentTimeMillis

    /**
     * 口型证据通道：`Live2dTouch: mouth v=0.477 t=…` 值-时间对，按 [Live2dMouthTrace]
     * 节流。真机验收拿它对齐素材包络（envclip.wav），不靠肉眼辨认「好像动过」。
     * 与 runtime 在不在无关——日志记的是喂进来的电平，画面没挂也不该丢证据。
     */
    private fun traceMouth(openness: Float) {
        val now = clockMs()
        if (!Live2dMouthTrace.shouldLog(now, mouthLastLogMs, openness, mouthWasOpen)) return
        Log.i(TAG, "mouth v=${"%.3f".format(openness)} t=$now")
        val (last, open) = Live2dMouthTrace.advanced(now, openness)
        mouthLastLogMs = last
        mouthWasOpen = open
    }

    /**
     * 舞台层认出的轻触（归一化坐标）。用 [live2dTapZone] 细分区（头/躯干/手/脚，
     * 阈值来自真机截图实测）→ 状态机迁移 → 表情名 → native 淡入淡出。
     * 上报给 agent 的 [stageTapRegion] 仍是 head/body 老语义——换引擎不换服务端契约。
     * 每次轻触一行 `Live2dTouch` 日志：真机验收的硬证据通道（同 demo #1 的 dhvrm）。
     */
    override fun onTouch(event: AvatarTouch): TouchResult {
        if (event.kind != AvatarTouchKind.TAP) return TouchResult.Ignored
        val region = live2dTapZone(event.x, event.y)
        reaction = Live2dTouchReaction.step(reaction, region)
        val expression = Live2dTouchReaction.expressionFor(reaction)
        runtime?.takeIf { viewCreated }?.setExpression(expression)
        Log.i(
            TAG,
            "tap region=$region costume=${reaction.costume} blushed=${reaction.blushed} " +
                "crab=${reaction.crab} sandal=${reaction.sandal} expression=$expression",
        )
        return TouchResult.Handled(region)
    }

    override fun release() {
        if (released) return
        released = true
        runtime?.release()
        runtime = null
        viewCreated = false
    }

    override fun createView(context: Context): View? {
        if (released) return null
        // Pass the Activity-bearing host context through — not applicationContext — so
        // Live2dRuntime can SetActivityInstance / build GLSurfaceView the same way the
        // pre-facade adapter did.
        val view = runtime(context).createView(context) ?: return null
        viewCreated = true
        // 主题色先于 View 到达时存过 → 挂上立刻补一帧，避免开场黑底闪一下。
        pendingBackgroundArgb?.let { setBackground(it) }
        return view
    }

    override fun availability(context: Context): DhEngineAvailability {
        val status = runtime(context).availability()
        return DhEngineAvailability(
            id = id,
            available = status.available,
            reason = status.reason,
            needsDownload = !status.available && status.reason == Live2dRuntime.REASON_NOT_DOWNLOADED,
        )
    }

    private fun runtime(context: Context): Live2dRuntime =
        runtime ?: Live2dRuntime(
            context,
            DhLabBootstrap.assetStore(context),
            modelDirName,
            modelJsonName,
        ).also { runtime = it }

    private companion object {
        private const val TAG = "Live2dTouch"
    }
}
