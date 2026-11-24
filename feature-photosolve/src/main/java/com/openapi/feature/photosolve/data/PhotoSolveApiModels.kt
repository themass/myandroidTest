package com.openapi.feature.photosolve.data

/**
 * agent 独立视觉解题接口 `/v1/photosolve/solve` 的普通 JSON 数据结构。
 *
 * 字段命名和 docs/design/photo-solve-design.md 第 3 节一致；Gson 按字段名映射，
 * 新增字段不需要改这里（未知字段被忽略）。
 */
data class PhotoSolveApiResponse(
    val requestId: String? = null,
    val providerUsed: String? = null,
    val subject: String? = null,
    val questionFound: Boolean = true,
    val questions: List<Question> = emptyList(),
) {
    data class Question(
        val index: Int = 1,
        val text: String = "",
        val latex: String = "",
        val steps: List<Step> = emptyList(),
        val finalAnswer: String = "",
        val finalAnswerLatex: String = "",
        /** 思考过程（审题思路），旧服务端没有这个字段时为空。 */
        val thinking: String = "",
        val confidence: Double? = null,
    )

    data class Step(
        val index: Int = 1,
        val text: String = "",
        val latex: String = "",
    )
}

/** multipart 的表单字段（除图片外）。字段名必须和服务端 `@RequestParam` 一致。 */
data class PhotoSolveRequest(
    val clientRequestId: String,
    val text: String? = null,
    val subject: String? = null,
    val grade: String? = null,
)
