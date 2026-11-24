package com.openapi.feature.camerafx

/**
 * 手指滑动跟踪（纯逻辑，JVM 单测钉死）。
 *
 * 交互参考 finger-frame-effect-ai 的「手指入画滑动切换」——**只借交互设计，
 * 不引它的代码/素材**（该仓库无许可证文件）。实现全部基于我们已有的
 * HandLandmarker 21 点 + 自制资产。
 *
 * 算法（累加位移，不是 A→B 直线）：
 * 1. 每帧取主手食指尖（点 8）；坐标先映射到**镜像显示空间**（x_m = 1 − x），
 *    用户在预览里往哪边滑，方向就是哪边——与线框/HUD 的 mirrorX 口径一致；
 * 2. 单帧位移小于 [jitterEpsilon] 视为检测噪声，不进累加器；
 * 3. 逐帧位移**带符号累加**：原地晃手来回抵消 → 累加器 ≈ 0，天然抑制误触
 *    （这是比「两点距离」强的一点：A→B→A 的两点法会因为采样相位误报）；
 * 4. 累加位移 ≥ [minDistance]（宽度归一化）且在 [maxStrokeFrames] 帧内完成
 *    → 判定一次滑动；主轴位移必须 ≥ 副轴 × [axisRatio]，斜着划不算；
 * 5. 触发后 [cooldownFrames] 帧内不再触发（防连发，与 [StageGestureEdge] 同纪律）。
 *
 * 宽高各向异性：归一化坐标相对宽/高分别归一，竖屏 3:4 下同样的归一化数值
 * 纵向实际距离更大——用 imgH/imgW 把 y 换算成**宽单位**再比。
 *
 * 手丢失容错：帧里没有手时保持锚点 [graceFrames] 帧不累加（漏检不等于滑回原点），
 * 超过宽限才复位——与贴纸的 [StickerHoldTracker] 同思路。
 *
 * 单线程使用（与 FxAnalyzer / UI 帧流同纪律），无锁。隐私红线同 [FxAnalyzer]：
 * 关键点只在内存里过，不落盘不上传。
 */
enum class SwipeDirection { NONE, LEFT, RIGHT, UP, DOWN }

class FingerSwipeTracker(
    /** 触发滑动的最小累加位移（宽单位）。 */
    private val minDistance: Float = 0.18f,
    /** 一次滑动的最长持续帧数（analysis 帧；pose+hand 每 2 帧跑一次，实际手样帧数减半）。 */
    private val maxStrokeFrames: Int = 24,
    /** 触发后的冷却帧数。 */
    private val cooldownFrames: Int = CameraGestureCue.DEFAULT_COOLDOWN_FRAMES,
    /** 小于此值的单帧位移当噪声丢弃。 */
    private val jitterEpsilon: Float = 0.006f,
    /** 主轴必须 ≥ 副轴 × 该比值（约 29° 以内才算直线）。 */
    private val axisRatio: Float = 1.6f,
    /** 手丢失的宽限帧数。 */
    private val graceFrames: Int = 4,
    /** 跟踪哪个指尖；8=食指尖（滑动交互的主手指）。 */
    private val tipIndex: Int = 8,
) {

    private var frame = 0L
    private var cooldownUntil = 0L
    private var anchor: Point2d? = null
    private var missRun = 0
    private var strokeStart = -1L
    private var accX = 0f
    private var accY = 0f

    /**
     * 喂一帧的手。返回本次判定出的方向（一次性；不触发时 [SwipeDirection.NONE]）。
     *
     * 多手时优先沿用上帧跟踪的那只（离锚点最近），否则取第一只有完整 21 点的手——
     * MediaPipe 的 hands 顺序不保证稳定，按「最近邻续接」而不是按下标。
     */
    fun update(hands: List<HandFx>, imgW: Int, imgH: Int): SwipeDirection {
        frame += 1
        val tip = pickTip(hands)
        if (tip == null) {
            missRun += 1
            if (missRun > graceFrames) {
                // 手离开太久：锚点与累加器一并作废——重新出现只立新锚，
                // 不从旧锚攒位移（否则「手消失后在别处出现」= 一次假滑动）。
                resetStroke()
                anchor = null
            }
            return SwipeDirection.NONE
        }
        missRun = 0
        // 镜像显示空间：用户所见即方向所见（前摄预览 mirrorX）。
        val p = Point2d(1f - tip.x, tip.y)
        val prev = anchor
        anchor = p
        if (prev == null) return SwipeDirection.NONE
        if (frame < cooldownUntil) {
            // 冷却中：位移不累加，锚点照常跟（否则出冷却第一帧会攒出一个假的大位移）。
            accX = 0f
            accY = 0f
            strokeStart = -1L
            return SwipeDirection.NONE
        }

        val aspect = if (imgW > 0) imgH.toFloat() / imgW.toFloat() else 1f
        var dx = p.x - prev.x
        var dy = (p.y - prev.y) * aspect
        if (kotlin.math.abs(dx) < jitterEpsilon) dx = 0f
        if (kotlin.math.abs(dy) < jitterEpsilon) dy = 0f
        if (dx == 0f && dy == 0f) return SwipeDirection.NONE

        if (accX == 0f && accY == 0f) strokeStart = frame
        accX += dx
        accY += dy

        if (frame - strokeStart > maxStrokeFrames) {
            // 划得太慢（或中途停太久）：不算滑动，从当前重新起笔。
            resetStroke()
            return SwipeDirection.NONE
        }

        val ax = kotlin.math.abs(accX)
        val ay = kotlin.math.abs(accY)
        val dominant = when {
            ax >= ay * axisRatio && ax >= minDistance ->
                if (accX > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
            ay >= ax * axisRatio && ay >= minDistance ->
                if (accY > 0) SwipeDirection.DOWN else SwipeDirection.UP
            else -> SwipeDirection.NONE
        }
        if (dominant == SwipeDirection.NONE) return SwipeDirection.NONE

        cooldownUntil = frame + cooldownFrames
        resetStroke()
        return dominant
    }

    /** 重新打开相机/小窗时复位。 */
    fun reset() {
        frame = 0
        cooldownUntil = 0L
        anchor = null
        missRun = 0
        resetStroke()
    }

    private fun resetStroke() {
        strokeStart = -1L
        accX = 0f
        accY = 0f
    }

    private fun pickTip(hands: List<HandFx>): Point2d? {
        val usable = hands.mapNotNull { h -> h.points.getOrNull(tipIndex)?.let { h to it } }
        if (usable.isEmpty()) return null
        val prev = anchor
            ?: return usable.first().second
        // 续接上帧：取离锚点（镜像空间）最近的指尖。
        return usable.minByOrNull { (_, t) ->
            val d = (1f - t.x) - prev.x
            val e = t.y - prev.y
            d * d + e * e
        }?.second
    }
}

/** HUD 短标签（与 [HandGestureKind.hudLabel] 同风格）；触发时刻的方向快照。 */
fun SwipeDirection.hudLabel(): String = when (this) {
    SwipeDirection.NONE -> "—"
    SwipeDirection.LEFT -> "←"
    SwipeDirection.RIGHT -> "→"
    SwipeDirection.UP -> "↑"
    SwipeDirection.DOWN -> "↓"
}

/**
 * 滑动方向 → 舞台词表 cue（单一事实源，同 [CameraGestureCue] 纪律）。
 *
 * 一期词表只给横滑配特效（`swipe` → 自制 `fx_swipe` 流光划过）；上下滑暂无
 * 对应舞台动作，返回 null 不硬凑。方向（LEFT/RIGHT）保留在 tracker 输出里，
 * 供 lab HUD 验证与后续「滑动翻页」类语义使用。
 *
 * **一期已知偏差**（如实记录）：cue 走既有 `cmp.cue → gestureSeq` 流，是**无方向**
 * 的（协议载荷只有 gesture id）。因此 `fx_swipe` 素材是一条**中性**流光（中间亮起
 * 两端淡出），不指方向；要按方向推流光，得给 gesture 载荷扩方向字段——协议改动
 * 不在本线范围（红线：不碰通话协议）。
 */
object CameraSwipeCue {
    const val STAGE_GESTURE_SWIPE = "swipe"

    fun stageGestureId(dir: SwipeDirection): String? = when (dir) {
        SwipeDirection.LEFT, SwipeDirection.RIGHT -> STAGE_GESTURE_SWIPE
        SwipeDirection.UP, SwipeDirection.DOWN, SwipeDirection.NONE -> null
    }
}
