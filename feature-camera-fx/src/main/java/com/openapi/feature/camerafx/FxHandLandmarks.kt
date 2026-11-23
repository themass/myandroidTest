package com.openapi.feature.camerafx

/**
 * MediaPipe Hand Landmarker 21 点拓扑（每只手）。
 * 索引语义：https://developers.google.com/mediapipe/solutions/vision/hand_landmarker
 */

const val HAND_LANDMARK_COUNT = 21

/** 手骨架连线：腕→各指链（拇指 1–4，食/中/无/小 各 3 节）。 */
val HAND_CONNECTIONS: Array<IntArray> = arrayOf(
    // 拇指
    intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 4),
    // 食指
    intArrayOf(0, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 8),
    // 中指
    intArrayOf(0, 9), intArrayOf(9, 10), intArrayOf(10, 11), intArrayOf(11, 12),
    // 无名指
    intArrayOf(0, 13), intArrayOf(13, 14), intArrayOf(14, 15), intArrayOf(15, 16),
    // 小指
    intArrayOf(0, 17), intArrayOf(17, 18), intArrayOf(18, 19), intArrayOf(19, 20),
)

/** 指尖索引，供手势启发式用。 */
val HAND_FINGER_TIPS: IntArray = intArrayOf(4, 8, 12, 16, 20)
