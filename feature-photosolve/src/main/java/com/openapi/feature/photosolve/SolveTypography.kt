package com.openapi.feature.photosolve

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * A42-#1：解题阅读内容四档字号的**单一事实源**。
 *
 * 之前是三层事实源各调各的：主题层只 pin 三档（titleLarge/titleMedium/labelLarge），
 * `SolveResultSections` 在调用点硬编码 17/15/14/18sp，`MathText` 里公式还要再 ×1.15。
 * 用户说「字体还是太大」时改一处漏两处——现在四档全部收口在这里，页面调用点与
 * MathText 默认值都只引用 token；守卫见 `PhotosolveTypographyGuardTest`
 * （页面级 MathText 出现字面量 sp 直接红）。
 *
 * 取值（相对全站正文基线 [com.openapi.common.ui.theme.AppTypography].body=15sp）：
 * - question 16：题目比正文略重一档即可（17→16；A42 取证：17/18 高于基线是「大」的主因之一）；
 * - step 15：与正文基线同档；
 * - thinking 14：辅助内容退一号 + secondary 色，与正式步骤拉开层次；
 * - answer 16：与题目同档。答案卡靠**底色与位置**强调，不靠再放大字号（18→16）。
 *
 * 改字号只改这里；公式相对正文的系数（[LATEX_SIZE_FACTOR]）同样只在这里调。
 */
object SolveTypography {
    val question: TextUnit = 16.sp
    val step: TextUnit = 15.sp
    val thinking: TextUnit = 14.sp
    val answer: TextUnit = 16.sp

    /**
     * 公式字号 = 正文字号 × 此系数。jlatexmath 的行内公式默认略小，需要一点放大，
     * 但旧的 1.15 叠在 17/18sp 正文上把公式顶得过大（A42 取证），降到 1.05。
     */
    const val LATEX_SIZE_FACTOR: Float = 1.05f
}
