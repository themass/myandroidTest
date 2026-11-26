package com.openapi.feature.video.prototype

/**
 * 新播放页的倍速档位单一事实源。
 *
 * - `panelSteps`：长按倍速键开面板精确选的全部档位（升序）。
 * - `cycleSteps`：单击倍速 chip 的循环序列，文案即当前态；不在序列里的当前值
 *   （比如面板里选了 0.5 或 2.5）单击时回到 1×。
 *
 * 注意：screening/ 目录里那份档位数组不在本页范围，协调会话后收，这里不改它。
 */
internal object SpeedSteps {

    /** 面板精确选的全部档位。 */
    val panelSteps: FloatArray = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)

    /** 单击循环：1 → 1.25 → 1.5 → 2 → 3 → 0.75 → 1。 */
    val cycleSteps: FloatArray = floatArrayOf(1f, 1.25f, 1.5f, 2f, 3f, 0.75f)

    /** 当前倍速单击后的下一档；当前值不在循环里时回 1×。 */
    fun nextAfter(current: Float): Float {
        val index = cycleSteps.indexOfFirst { it == current }
        if (index < 0) return 1f
        return cycleSteps[(index + 1) % cycleSteps.size]
    }
}
