package com.openapi.feature.photosolve.markdown

/**
 * 模型输出里的「Markdown + LaTeX」文本的纯函数处理，不依赖 Android，可以直接 JVM 单测。
 *
 * - [normalize]：渲染之前统一定界符。Markwon 的 JLatexMath 插件只认 `$$...$$`，单美元符号行内公式由
 *   [SingleDollarLatexPlugin] 补；模型有时会写成 `\( \)` / `\[ \]`，这里转成上面两种形式。
 * - [plain]：把公式转成可读的纯文本，给**列表预览**用（列表里每行都起一个公式渲染成本太高）。
 */
object MathMarkdown {

    private val BRACKET_BLOCK = Regex("""\\\[(.+?)\\\]""", RegexOption.DOT_MATCHES_ALL)
    private val PAREN_INLINE = Regex("""\\\((.+?)\\\)""", RegexOption.DOT_MATCHES_ALL)

    /** 转成 Markwon 能渲染的形式。 */
    fun normalize(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        var s: String = raw
        // JSON 或流式拼接里可能多了一层反斜杠：\\( → \(
        for (i in 0 until 4) {
            val next = s.replace("\\\\[", "\\[").replace("\\\\]", "\\]")
                .replace("\\\\(", "\\(").replace("\\\\)", "\\)")
            if (next == s) break
            s = next
        }
        // \[ ... \] → 独立成行的 $$ 块；\( ... \) → 行内 $...$
        s = BRACKET_BLOCK.replace(s) { "\n\n\$\$\n" + it.groupValues[1].trim() + "\n\$\$\n\n" }
        s = PAREN_INLINE.replace(s) { "\$" + it.groupValues[1].trim() + "\$" }
        return s
    }

    // ---- 纯文本预览 ----

    private val DOUBLE_BACKSLASH_COMMAND = Regex("""\\\\([a-zA-Z])""")
    private val LAYOUT_SPACE = Regex("""\\[,;:! ]""")
    private val FRAC = Regex("""\\[dt]?frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}""")
    private val SQRT_N = Regex("""\\sqrt\s*\[([^\]]*)\]\s*\{([^{}]*)\}""")
    private val SQRT = Regex("""\\sqrt\s*\{([^{}]*)\}""")
    private val TEXT_LIKE = Regex("""\\(?:text|mathrm|mathbf|textbf|mathit|operatorname)\s*\{([^{}]*)\}""")
    private val POWER = Regex("""\^\{([^{}]*)\}|\^([0-9a-zA-Z+\-])""")
    private val SUBSCRIPT = Regex("""_\{([^{}]*)\}|_([0-9a-zA-Z])""")
    private val COMMAND = Regex("""\\([a-zA-Z]+)""")
    private val WHITESPACE = Regex("""\s+""")

    private val SUPERSCRIPT = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶',
        '7' to '⁷', '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻', 'n' to 'ⁿ',
    )
    private val SUBSCRIPT_DIGITS = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆',
        '7' to '₇', '8' to '₈', '9' to '₉',
    )

    private val SYMBOLS = mapOf(
        "times" to "×", "div" to "÷", "pm" to "±", "mp" to "∓", "leq" to "≤", "le" to "≤",
        "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠", "approx" to "≈", "pi" to "π",
        "cdot" to "·", "infty" to "∞", "angle" to "∠", "triangle" to "△", "therefore" to "∴",
        "because" to "∵", "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "theta" to "θ", "lambda" to "λ", "mu" to "μ", "sigma" to "σ", "omega" to "ω", "Delta" to "Δ",
        "sum" to "∑", "int" to "∫", "in" to "∈", "subset" to "⊂", "cup" to "∪", "cap" to "∩",
        "rightarrow" to "→", "to" to "→", "Rightarrow" to "⇒", "left" to "", "right" to "",
        "quad" to " ", "qquad" to " ", "ldots" to "…", "cdots" to "…", "perp" to "⊥",
        "parallel" to "∥", "circ" to "°",
    )

    /** 公式转纯文本。只用于列表预览，不追求完整，追求"一眼能看懂是哪道题"。 */
    fun plain(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s: String = raw
        s = s.replace("\$", "")
        s = s.replace("\\(", "").replace("\\)", "").replace("\\[", "").replace("\\]", "")
        s = DOUBLE_BACKSLASH_COMMAND.replace(s) { "\\" + it.groupValues[1] }
        s = s.replace("\\\\", " ")
        s = LAYOUT_SPACE.replace(s, " ")
        s = s.replace("^{\\circ}", "°").replace("^\\circ", "°")

        s = TEXT_LIKE.replace(s) { it.groupValues[1] }
        // 嵌套的分式/根号：每轮只能匹配最内层，多跑几轮
        for (i in 0 until 4) {
            val before = s
            s = FRAC.replace(s) { wrap(it.groupValues[1]) + "/" + wrap(it.groupValues[2]) }
            s = SQRT_N.replace(s) { radicalPrefix(it.groupValues[1]) + wrap(it.groupValues[2]) }
            s = SQRT.replace(s) { "√" + wrap(it.groupValues[1]) }
            if (s == before) break
        }
        s = POWER.replace(s) { m -> superscript(m.groupValues[1].ifEmpty { m.groupValues[2] }) }
        s = SUBSCRIPT.replace(s) { m -> subscript(m.groupValues[1].ifEmpty { m.groupValues[2] }) }
        s = COMMAND.replace(s) { m -> SYMBOLS[m.groupValues[1]] ?: m.groupValues[1] }
        s = s.replace("{", "").replace("}", "").replace("**", "")
        return WHITESPACE.replace(s, " ").trim()
    }

    /** 单个字符/数字不加括号，复合表达式加括号。 */
    private fun wrap(x: String): String {
        val t = x.trim()
        return if (t.length <= 1 || t.all { it.isLetterOrDigit() }) t else "($t)"
    }

    private fun radicalPrefix(index: String): String = when (index.trim()) {
        "2", "" -> "√"
        "3" -> "∛"
        "4" -> "∜"
        else -> index.trim() + "√"
    }

    private fun superscript(x: String): String {
        val t = x.trim()
        if (t.isNotEmpty() && t.all { it in SUPERSCRIPT }) return t.map { SUPERSCRIPT.getValue(it) }.joinToString("")
        return if (t.length <= 1) "^$t" else "^($t)"
    }

    private fun subscript(x: String): String {
        val t = x.trim()
        return if (t.isNotEmpty() && t.all { it in SUBSCRIPT_DIGITS }) {
            t.map { SUBSCRIPT_DIGITS.getValue(it) }.joinToString("")
        } else {
            t // 字母下标（x_n、a_{AB}）去掉下划线直接并排
        }
    }
}
