package com.openapi.ks.myapp.dh

import android.content.Context
import android.util.Log
import android.view.View
import com.openapi.ks.dh.EngineAssets
import com.openapi.ks.dh.Live2dRuntime
import com.openapi.ks.myapp.avatar.AvatarCapability
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.AvatarTouchKind
import com.openapi.ks.myapp.avatar.TouchResult
import kotlin.random.Random

/**
 * Live2D 适配器：直接调 `:dh-engines` 的 [Live2dRuntime]（已内置 Java/JNI 包装，无反射）。
 * 素材没下好（LIVE2D 清单仍空，或 cache 里还没有模型）时 [availability] 回「未下载」并标
 * [DhEngineAvailability.needsDownload]；JNI 包装库缺失时回「native 库缺失」并 blocked。
 * 口型走 [Live2dRuntime.setMouth]，情绪走 [Live2dRuntime.setExpression]。
 *
 * 触点互动（demo #2 → v2 → **触摸刀 C6**）：[onTouch] 吃舞台层认出的轻触（归一化坐标），
 * 反应**从声明来**：命中区与候选反应读自模型包里的 `wardrobe-spec.json` 的
 * `touchReactions`（[Live2dPackFacts]），路由与抽签是纯决策层（[TouchZoneRouter] /
 * [TouchReactionDecision]），三级回落 = 声明 → 模型自带 → 兜底分区 + 循环。
 * tap 不再需要任何托盘/菜单配合：点下去这一发就地生效（0 跳），长按才是托盘。
 *
 * 「哪个形象能动哪几根轴」也不再按目录名写死（旧代码是 `fullWardrobe = dir == "ATRI"`）：
 * 闸来自包自己注册的**表情名集合**（[Live2dPackFacts.Facts.registered]）。ATRI 注册 180 个
 * 组合表情所以四轴全开；Amadeus/Yuuka 只注册 base+blush，于是它们的脸轴只在两者间循环
 * （循环会跳过没有资产的那一格），其余轴的结构化结果就是「没有可用反应」→ [TouchResult.Ignored]，
 * 与旧的最小层同手感，但那是资产事实推出来的，不是代码里的分支。
 *
 * 表情名对应的 exp3 资产由 lab 清单注册（scripts/dh-lab/live2d-lab-assets/，seed-assets.sh
 * live2d 推送；同一目录也推 wardrobe-spec.json，见 [Live2dPackFacts]）。
 */
class Live2dDigitalHumanEngine(
    private val initialModelDirName: String = Live2dRuntime.DEFAULT_MODEL_DIR,
) : DigitalHumanEngine {

    override val id: DhEngineId = DhEngineId.LIVE2D

    /** 热换形象后更新；构造时的 [initialModelDirName] 只是首帧默认。 */
    private var activeModelDir: String = initialModelDirName

    private val modelJsonName get() = "$activeModelDir.model3.json"

    override val capabilities: Set<AvatarCapability> = setOf(
        AvatarCapability.MOUTH,
        AvatarCapability.EMOTION,
        AvatarCapability.GAZE,
        AvatarCapability.STATE,
        AvatarCapability.TOUCH_TAP,
    )

    /**
     * 当前运行时能吃哪几类 cue（能力门，[TouchReactionDecision] 用它过滤候选）。
     *
     * fork 的 JNI 只有 `nativeApplyExpression`（`JniBridgeJava` 无 motion API），所以
     * `motion` 类声明今天**不参与抽签**——声明与校验先行，fork 补上 motion API 后把
     * [TouchCueKind.MOTION] 加进来即可，不用再动结构。
     */
    private val supportedCueKinds: Set<TouchCueKind> = setOf(TouchCueKind.VARIANT, TouchCueKind.CYCLE)

    private var runtime: Live2dRuntime? = null
    private var viewCreated = false
    private var released = false

    /** 触点互动状态（四轴循环位）；release 后重建引擎会回到初始态——与画面一致。 */
    private var reaction = Live2dTouchReactionState()

    /** 上一发播过的 cue：连点同一处要换样（只剩它时不排除，见 pickWeighted）。 */
    private var lastCueKey: String? = null

    /** 抽签取数；单测注入固定值让「随机」可断言。 */
    internal var rollSource: () -> Int = { Random.nextInt(1000) }

    /** createView / availability 拿到的 host Context（读包事实用；JVM 单测里为 null）。 */
    private var hostContext: Context? = null

    /** 按模型目录缓存的包事实；换形象（[switchToModel]）自动失效。 */
    private var factsCache: Pair<String, Live2dPackFacts.Facts>? = null

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
     * 舞台层认出的轻触（归一化坐标）→ **直达反应**（触摸刀 C6 的落点）。
     *
     * 链路：命中区（声明或兜底）→ 候选按能力门 + 表情资产闸筛 → 权重抽签 → 状态机 →
     * `nativeApplyExpression`。整条在本地闭环，不等服务端：点了必有反应（真机投诉
     * 2026-11-23「只能点一次」的根治语义保留），上报给 agent 的 region 语义不变。
     *
     * 没有可用反应时回 [TouchResult.Ignored]——「点了没动静」的假轴不做；
     * 每一次轻触一行 `Live2dTouch` 日志（含 level/zone/cue），真机验收的硬证据通道。
     */
    override fun onTouch(event: AvatarTouch): TouchResult {
        if (event.kind != AvatarTouchKind.TAP) return TouchResult.Ignored
        val facts = packFacts()
        val declaration = facts.declaration ?: TouchZoneRouter.builtIn
        val zone = TouchZoneRouter.hit(declaration, event.x, event.y)
        val choice = TouchReactionDecision.resolveTap(
            hit = zone,
            modelOwnCues = facts.modelOwnCues,
            supported = supportedCueKinds,
            lastCueKey = lastCueKey,
            roll = rollSource(),
            state = reaction,
            expressionGate = facts::isRegistered,
        )
        if (!choice.changed) {
            Log.i(TAG, "tap model=$activeModelDir zone=${zone?.id ?: "-"} level=${choice.level} -> ignored")
            return TouchResult.Ignored
        }
        reaction = choice.state
        lastCueKey = choice.cue?.key()
        val expression = Live2dTouchReaction.expressionFor(reaction)
        runtime?.takeIf { viewCreated }?.setExpression(expression)
        // 命中区 id 就是上报给 agent 的 region（head/body/hand/foot 与旧词表一致）；万一声明里
        // 用了新 id，退到旧的竖向二分，服务端契约不被换刀打断。日志保持 `tap model=… region=…`
        // 前缀不变（真机验收脚本 grep 的就是它），换刀新增的 level/cue 追加在后面。
        val region = choice.region ?: stageTapRegion(event.x, event.y)
        Log.i(
            TAG,
            "tap model=$activeModelDir region=$region level=${choice.level} cue=${choice.cue?.key()} " +
                "top=${reaction.top} shoes=${reaction.shoes} held=${reaction.held} face=${reaction.face} " +
                "expression=$expression",
        )
        return TouchResult.Handled(region)
    }

    // ---- 换装托盘（长按呼出）：轴推进与分区点击共用同一状态机 ----

    /**
     * 托盘行：可用轴由**表情资产**判定（这根轴上至少有一格表情在这个模型注册过），
     * 不再按目录名写死。pending 轴（内衣/头饰）照旧灰显带原因——那是「声明出来的不可用」。
     */
    override fun wardrobeRows(): List<Live2dWardrobeRow> {
        val facts = packFacts()
        return Live2dTouchReaction.wardrobeRows(reaction).map { row ->
            if (row.enabled && !Live2dTouchReaction.axisHasAssets(row.axis, reaction, facts::isRegistered)) {
                row.copy(enabled = false, disabledReason = "这个形象的素材里没有对应表情")
            } else {
                row
            }
        }
    }

    override fun advanceWardrobe(axis: Live2dWardrobeAxis) {
        val facts = packFacts()
        if (!Live2dTouchReaction.axisHasAssets(axis, reaction, facts::isRegistered)) return
        val before = reaction
        reaction = Live2dTouchReaction.advanceWardrobe(reaction, axis)
        if (reaction == before) return
        val expression = Live2dTouchReaction.expressionFor(reaction)
        runtime?.takeIf { viewCreated }?.setExpression(expression)
        Log.i(TAG, "wardrobe model=$activeModelDir axis=$axis expression=$expression")
    }

    override fun modelLabel(): String = activeModelDir

    /** 同一块 GL View 热换形象目录；素材未就绪时 false。 */
    fun switchToModel(context: Context, targetDirName: String): Boolean {
        if (released || !viewCreated) return false
        if (targetDirName == activeModelDir) return true
        val probe = Live2dRuntime(context, DhLabBootstrap.assetStore(context), targetDirName)
        val ok = probe.availability().available
        probe.release()
        if (!ok) return false
        runtime(context).changeModel(targetDirName, "$targetDirName.model3.json")
        activeModelDir = targetDirName
        reaction = live2dReactionAfterModelSwitch(targetDirName)
        lastCueKey = null
        factsCache = null // 换形象 = 换包：声明与表情注册表都要重读
        Log.i(TAG, "hotswap model->$targetDirName")
        return true
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
        hostContext = context
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
        hostContext = context
        val status = runtime(context).availability()
        return DhEngineAvailability(
            id = id,
            available = status.available,
            reason = status.reason,
            needsDownload = !status.available && status.reason == Live2dRuntime.REASON_NOT_DOWNLOADED,
        )
    }

    /**
     * 当前形象包的触摸事实（声明 + 表情注册表 + 自带动作）。
     *
     * 读不到包（JVM 单测里没建 View、或素材还没下载）→ [Live2dPackFacts.EMPTY]：
     * 命中退兜底分区、表情闸退成放行，也就是**与触摸刀之前完全一致**的行为，
     * 而不是把「读不到」当成「什么都没有」而全盘 Ignored。
     */
    private fun packFacts(): Live2dPackFacts.Facts {
        factsCache?.takeIf { it.first == activeModelDir }?.let { return it.second }
        val ctx = hostContext ?: return Live2dPackFacts.EMPTY
        val root = runCatching { DhLabBootstrap.assetStore(ctx).cacheRoot(EngineAssets.LIVE2D) }.getOrNull()
            ?: return Live2dPackFacts.EMPTY
        if (!root.isDirectory) return Live2dPackFacts.EMPTY
        val facts = Live2dPackFacts.load(
            Live2dPackFacts.modelDir(root, activeModelDir),
            modelJsonName,
        )
        facts.errors.forEach { Log.w(TAG, "pack facts ($activeModelDir): $it") }
        factsCache = activeModelDir to facts
        return facts
    }

    private fun runtime(context: Context): Live2dRuntime =
        runtime ?: Live2dRuntime(
            context,
            DhLabBootstrap.assetStore(context),
            activeModelDir,
            modelJsonName,
        ).also { runtime = it }

    private companion object {
        private const val TAG = "Live2dTouch"
    }
}
