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
 * 触点互动（demo #2 → v2）：[onTouch] 吃舞台层认出的轻触（归一化坐标），用
 * [Live2dTouchReaction] 纯状态机决定表情名——四根轴各自循环（头→表情、躯干→上衣、
 * 手→手持、脚→鞋），每次点击都进新的可见状态（真机投诉 2026-11-23「只能点一次」
 * 的根治），再经 `nativeApplyExpression` 交给 native 交叉淡入淡出。表情名对应的 exp3
 * 资产由 lab 清单注册（scripts/dh-lab/live2d-lab-assets/，seed-assets.sh live2d 推送）。
 *
 * 多形象（用户 2026-11-23「demo里有好几个形象」）：[modelDirName] 选 ATRI / Amadeus /
 * Yuuka。后两个没有上游已验证的换装参数（盘点：Yuuka 零 exp3、Amadeus 只有 logo 开关），
 * 只有跨模型语义一致的 ParamCheek——所以它们走 [Live2dTouchReaction.stepFaceOnly]
 * 最小层（点头 正常↔脸红），换装托盘只给脸轴；其余区域回 Ignored，不做假轴。
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

    /** 只有 ATRI 有全套已验证换装资产；其余模型走最小脸轴（见类注释）。 */
    private val fullWardrobe: Boolean get() = modelDirName == "ATRI"

    private var runtime: Live2dRuntime? = null
    private var viewCreated = false
    private var released = false

    /** 触点互动状态（四轴循环位）；release 后重建引擎会回到初始态——与画面一致。 */
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
     * 阈值来自真机截图实测）→ v2 循环状态机 → 表情名 → native 淡入淡出。
     * ATRI 四轴全开；Amadeus/Yuuka 只有头轴有效（最小脸层），其余区域回 Ignored
     * ——「点了没动静」的假轴不做。上报给 agent 的 [stageTapRegion] 仍是 head/body
     * 老语义——换引擎不换服务端契约。每次轻触一行 `Live2dTouch` 日志：真机验收的
     * 硬证据通道（同 demo #1 的 dhvrm）。
     */
    override fun onTouch(event: AvatarTouch): TouchResult {
        if (event.kind != AvatarTouchKind.TAP) return TouchResult.Ignored
        val region = live2dTapZone(event.x, event.y)
        val before = reaction
        reaction = if (fullWardrobe) {
            Live2dTouchReaction.step(reaction, region)
        } else {
            Live2dTouchReaction.stepFaceOnly(reaction, region)
        }
        if (reaction == before) return TouchResult.Ignored
        val expression = if (fullWardrobe) {
            Live2dTouchReaction.expressionFor(reaction)
        } else {
            Live2dTouchReaction.expressionForFaceOnly(reaction)
        }
        runtime?.takeIf { viewCreated }?.setExpression(expression)
        Log.i(
            TAG,
            "tap model=$modelDirName region=$region top=${reaction.top} shoes=${reaction.shoes} " +
                "held=${reaction.held} face=${reaction.face} expression=$expression",
        )
        return TouchResult.Handled(region)
    }

    // ---- 换装托盘（长按呼出）：轴推进与分区点击共用同一状态机 ----

    override fun wardrobeRows(): List<Live2dWardrobeRow> =
        if (fullWardrobe) {
            Live2dTouchReaction.wardrobeRows(reaction)
        } else {
            Live2dTouchReaction.wardrobeRows(reaction).filter { it.axis == Live2dWardrobeAxis.FACE }
        }

    override fun advanceWardrobe(axis: Live2dWardrobeAxis) {
        if (!fullWardrobe && axis != Live2dWardrobeAxis.FACE) return
        reaction = Live2dTouchReaction.advanceWardrobe(reaction, axis)
        val expression = if (fullWardrobe) {
            Live2dTouchReaction.expressionFor(reaction)
        } else {
            Live2dTouchReaction.expressionForFaceOnly(reaction)
        }
        runtime?.takeIf { viewCreated }?.setExpression(expression)
        Log.i(TAG, "wardrobe model=$modelDirName axis=$axis expression=$expression")
    }

    override fun modelLabel(): String = modelDirName

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
