package com.openapi.feature.photosolve.history

import com.openapi.feature.photosolve.markdown.MathMarkdown

/** 一条解题记录（已保存）。 */
data class SolveRecord(
    val id: Long,
    /** 客户端请求 id，同一次请求只会存一条（幂等）；Mock 或旧数据可能为空。 */
    val requestId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** 列表上显示的标题；默认取题干前 24 个字，用户可以重命名。 */
    val title: String,
    /** 题干，Markdown + LaTeX。 */
    val question: String,
    val steps: List<String>,
    val answer: String,
    /** 思考过程，可能为空（旧服务端、模型没给）。 */
    val thinking: String,
    val subject: String,
    val favorite: Boolean,
    /** 裁剪后的题目图（长边 ≤ 1280），可能因磁盘错误缺失。 */
    val imagePath: String?,
    /** 列表缩略图（长边 ≤ 240）。 */
    val thumbPath: String?,
)

/** 准备保存的一条记录（还没有 id、时间戳和图片路径）。 */
data class SolveDraft(
    val requestId: String?,
    val question: String,
    val steps: List<String>,
    val answer: String,
    val thinking: String,
    val subject: String,
)

/** 记录的编码与默认标题。纯函数，可 JVM 单测。 */
object SolveRecordCodec {

    const val UNTITLED = "未命名题目"
    const val TITLE_MAX = 24

    /** ASCII 记录分隔符（0x1E）：不会出现在模型输出里，用来把步骤列表存成一列。 */
    private const val STEP_SEPARATOR = "\u001E"

    fun encodeSteps(steps: List<String>): String = steps.joinToString(STEP_SEPARATOR)

    fun decodeSteps(raw: String?): List<String> =
        if (raw.isNullOrEmpty()) emptyList() else raw.split(STEP_SEPARATOR)

    /** 默认标题：题干转成纯文本后取前 [TITLE_MAX] 个字。 */
    fun defaultTitle(question: String?): String {
        val plain = MathMarkdown.plain(question)
        if (plain.isBlank()) return UNTITLED
        return if (plain.length > TITLE_MAX) plain.take(TITLE_MAX) + "…" else plain
    }
}
