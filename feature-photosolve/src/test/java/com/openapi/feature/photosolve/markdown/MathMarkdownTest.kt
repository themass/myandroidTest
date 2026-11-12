package com.openapi.feature.photosolve.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathMarkdownTest {

    // ---- normalize：定界符 ----

    @Test
    fun parenDelimitersBecomeInlineDollars() {
        assertEquals("已知 \$x^2\$ 求值", MathMarkdown.normalize("已知 \\(x^2\\) 求值"))
    }

    @Test
    fun bracketDelimitersBecomeBlockDollars() {
        val out = MathMarkdown.normalize("如下 \\[x=\\frac{-b}{2a}\\] 结束")
        assertTrue(out, out.contains("\$\$"))
        assertTrue(out, out.contains("x=\\frac{-b}{2a}"))
        assertFalse(out, out.contains("\\["))
    }

    @Test
    fun extraBackslashLayerIsCollapsed() {
        // JSON 多转义一层：\\(x\\) → \(x\) → $x$
        assertEquals("已知 \$x\$ 求值", MathMarkdown.normalize("已知 \\\\(x\\\\) 求值"))
    }

    @Test
    fun doubleDollarBlocksAreUntouched() {
        val src = "\$\$\nx^2+1\n\$\$"
        assertEquals(src, MathMarkdown.normalize(src))
    }

    @Test
    fun nullAndEmptyAreSafe() {
        assertEquals("", MathMarkdown.normalize(null))
        assertEquals("", MathMarkdown.normalize(""))
    }

    @Test
    fun normalizeIsIdempotent() {
        val once = MathMarkdown.normalize("已知 \\(x\\) 与 \\[y\\]")
        assertEquals(once, MathMarkdown.normalize(once))
    }

    // ---- plain：给列表预览 ----

    @Test
    fun fracBecomesSlashForm() {
        assertEquals("1/2", MathMarkdown.plain("\$\\frac{1}{2}\$"))
        assertEquals("(x+1)/(x-1)", MathMarkdown.plain("\$\\frac{x+1}{x-1}\$"))
    }

    @Test
    fun sqrtBecomesRadicalSign() {
        assertEquals("√3", MathMarkdown.plain("\$\\sqrt{3}\$"))
        // 立方根用单个字符 ∛，而不是「上标 3 + √」
        assertEquals("∛8", MathMarkdown.plain("\$\\sqrt[3]{8}\$"))
        // 其他次数没有对应字符，退回「次数 + √」
        assertEquals("5√32", MathMarkdown.plain("\$\\sqrt[5]{32}\$"))
    }

    @Test
    fun powersBecomeUnicodeSuperscripts() {
        assertEquals("x²-5x+6=0", MathMarkdown.plain("\$x^2-5x+6=0\$"))
        assertEquals("a⁻¹", MathMarkdown.plain("\$a^{-1}\$"))
    }

    @Test
    fun subscriptsBecomeUnicodeOrPlain() {
        // 数字下标转 Unicode 下标字符；字母下标没法转，去掉花括号直接并排
        assertEquals("x₁=2", MathMarkdown.plain("\$x_1=2\$"))
        assertEquals("x₁=2", MathMarkdown.plain("\$x_{1}=2\$"))
        assertEquals("aAB=1", MathMarkdown.plain("\$a_{AB}=1\$"))
    }

    @Test
    fun greekAndOperatorsBecomeSymbols() {
        assertEquals("α±β", MathMarkdown.plain("\$\\alpha\\pm\\beta\$"))
        assertEquals("π", MathMarkdown.plain("\$\\pi\$"))
        // 源里的空格保留：a\neq b 读起来就是 "a ≠ b"
        assertEquals("a≠ b", MathMarkdown.plain("\$a\\neq b\$"))
    }

    @Test
    fun textCommandKeepsItsContent() {
        assertEquals("5cm", MathMarkdown.plain("\$\\text{5cm}\$"))
    }

    @Test
    fun nestedFractionAndSqrtAreFlattened() {
        // 分子是复合表达式要加括号；分母 2a 本身不含运算符，不需要括号
        assertEquals("(-b±√(b²-4ac))/2a", MathMarkdown.plain("\$\\frac{-b\\pm\\sqrt{b^2-4ac}}{2a}\$"))
        assertEquals("1/(x+y)", MathMarkdown.plain("\$\\frac{1}{x+y}\$"))
    }

    @Test
    fun degreeSymbolIsHandled() {
        assertEquals("90°", MathMarkdown.plain("\$90^{\\circ}\$"))
    }

    @Test
    fun plainTextWithoutMathPassesThrough() {
        assertEquals("求下列各式的值", MathMarkdown.plain("求下列各式的值"))
    }

    @Test
    fun leftoverBracesAndSpacesAreCleaned() {
        val out = MathMarkdown.plain("\$  \\frac{ 1 }{ 2 }  \$")
        assertEquals("1/2", out)
        assertFalse(out.contains("{"))
    }

    @Test
    fun plainOfNullIsEmpty() {
        assertEquals("", MathMarkdown.plain(null))
        assertEquals("", MathMarkdown.plain("  "))
    }
}
