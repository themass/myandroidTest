package com.openapi.feature.camerafx

/**
 * 稀疏采样索引表：FaceLandmarker 有 478 点、PoseLandmarker 33 点，
 * 全画 478 会糊成一团，这里只取能表达「五官 + 头廓」的子集。
 * 索引语义来自 MediaPipe Face Mesh / BlazePose 官方定义（Apache-2.0）。
 */

/** 脸部外轮廓（36 点，首尾闭合）。也用来算人脸框与头顶点。 */
val FACE_OVAL: IntArray = intArrayOf(
    10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378,
    400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21,
    54, 103, 67, 109,
)

/** 左眉（人物视角的左，未镜像画面里在右边），5 点**开**折线（首尾不闭合）。 */
val FACE_BROW_LEFT: IntArray = intArrayOf(70, 63, 105, 66, 107)

/** 右眉，5 点开折线。 */
val FACE_BROW_RIGHT: IntArray = intArrayOf(46, 53, 64, 101, 99)

/** 左眼轮廓 6 点折线（首尾闭合）。 */
val FACE_EYE_LEFT: IntArray = intArrayOf(159, 145, 153, 154, 155, 133)

/** 右眼轮廓 6 点折线（首尾闭合）。 */
val FACE_EYE_RIGHT: IntArray = intArrayOf(33, 7, 163, 144, 143, 157)

/** 虹膜中心：两眼连线倾角就是拿这两点算的。 */
const val FACE_IRIS_CENTER_LEFT = 468
const val FACE_IRIS_CENTER_RIGHT = 473

/** 外唇轮廓（20 点，首尾闭合）。 */
val FACE_LIPS_OUTER: IntArray = intArrayOf(
    61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185,
)

/** 关键点总数（含虹膜）。 */
const val FACE_LANDMARK_COUNT = 478

/**
 * BlazePose 骨架连线（关节索引对）。主干 + 腕连指尖（粗粒度「手」扇形）。
 * 精细 21 点手型走 HandLandmarker（见 [HAND_CONNECTIONS]）。
 */
val POSE_CONNECTIONS: Array<IntArray> = arrayOf(
    // 头
    intArrayOf(0, 2), intArrayOf(0, 5), intArrayOf(2, 3), intArrayOf(5, 4), intArrayOf(3, 4),
    intArrayOf(2, 7), intArrayOf(5, 8),
    // 躯干
    intArrayOf(11, 12), intArrayOf(11, 23), intArrayOf(12, 24), intArrayOf(23, 24),
    // 手臂
    intArrayOf(11, 13), intArrayOf(13, 15), intArrayOf(12, 14), intArrayOf(14, 16),
    // BlazePose 手腕→指尖（15/16 腕，17–22 指尖）
    intArrayOf(15, 17), intArrayOf(15, 19), intArrayOf(15, 21),
    intArrayOf(16, 18), intArrayOf(16, 20), intArrayOf(16, 22),
    // 腿
    intArrayOf(23, 25), intArrayOf(25, 27), intArrayOf(24, 26), intArrayOf(26, 28),
    intArrayOf(27, 29), intArrayOf(29, 31), intArrayOf(28, 30), intArrayOf(30, 32),
)

const val POSE_LANDMARK_COUNT = 33

/** 姿态关节低于这个置信度就当没检测到，不画（否则背后幻影骨架乱飞）。 */
const val POSE_VISIBILITY_MIN = 0.5f
