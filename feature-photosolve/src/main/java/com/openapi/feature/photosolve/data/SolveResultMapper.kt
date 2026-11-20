package com.openapi.feature.photosolve.data

import com.openapi.feature.photosolve.presentation.PhotoSolveError
import com.openapi.feature.photosolve.presentation.PhotoSolveException
import com.openapi.feature.photosolve.presentation.PhotoSolveResult

/**
 * 服务端响应 → 界面用的结果。纯函数，不依赖 Android，可以直接 JVM 单测。
 *
 * 公式的约定：公式直接写在 text / thinking / finalAnswer 里（`$...$`、`$$...$$`），界面用 Markwon 渲染。
 * 旧版服务端（以及模型偶尔不听话）还会把公式单独放在 `latex` 字段里，所以 [merge] 负责把它并回文本，
 * 且在文本里已经包含同一个公式时不重复追加。
 */
object SolveResultMapper {

    fun map(api: PhotoSolveApiResponse): PhotoSolveResult {
        if (!api.questionFound || api.questions.isEmpty()) {
            throw PhotoSolveException(PhotoSolveError.NOT_A_QUESTION, "没有识别到题目")
        }
        // 当前界面一次展示一道题：取第一道，题目总数通过 questionCount 透传（多题切换见设计文档风险 8）
        val first = api.questions.first()
        return PhotoSolveResult(
            recognizedQuestion = merge(first.text, first.latex),
            steps = first.steps.mapNotNull { step -> merge(step.text, step.latex).takeIf { it.isNotBlank() } },
            finalAnswer = merge(first.finalAnswer, first.finalAnswerLatex),
            latex = null,
            requestId = api.requestId,
            providerUsed = api.providerUsed,
            questionCount = api.questions.size,
            thinking = first.thinking.trim(),
            subject = api.subject.orEmpty(),
        )
    }

    /** 把单独的 latex 并进文本；文本里已经有同一个公式（忽略空白）就不重复。 */
    internal fun merge(text: String, latex: String): String {
        val t = text.trim()
        val l = latex.trim()
        if (l.isEmpty()) return t
        val bare = l.removePrefix("\$\$").removeSuffix("\$\$").removePrefix("\$").removeSuffix("\$").trim()
        if (bare.isEmpty()) return t
        val compactText = t.filterNot { it.isWhitespace() }
        val compactBare = bare.filterNot { it.isWhitespace() }
        if (compactText.contains(compactBare)) return t
        val block = if (l.startsWith("\$")) l else "\$\$\n" + bare + "\n\$\$"
        return if (t.isEmpty()) block else t + "\n\n" + block
    }
}
