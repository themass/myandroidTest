package com.openapi.ks.myapp.companion.teaching

import com.openapi.ks.myapp.avatar.TeacherExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导演脚本的钉桩测试（纯决策层，无 Android）。
 * 重点：台词与 C2 已复核脚本一致；退出场景不出现表扬/特效（C1-X 硬性失败）。
 */
class TeachingScriptTest {

    @Test
    fun `每路场景都以用户固定输入开场以老师回复为第二步`() {
        DemoPath.entries.forEach { path ->
            val steps = scriptFor(path)
            assertEquals("$path 第1步必须是用户输入", StepKind.USER_LINE, steps.first().kind)
            assertEquals("$path 初始输入必须与旧版一致", path.initialPrompt, steps.first().text)
            assertEquals("$path 第2步必须是老师回复", StepKind.TEACHER_LINE, steps[1].kind)
        }
    }

    @Test
    fun `老师台词逐句来自 scriptedReply 原文`() {
        // 演出层不得即兴补话：每句 TEACHER_LINE 都等于对紧邻上一句用户输入的 scriptedReply
        // （corrected 两个取值之一），不新增措辞。
        DemoPath.entries.forEach { path ->
            var lastUserLine = ""
            scriptFor(path).forEach { step ->
                when (step.kind) {
                    StepKind.USER_LINE -> lastUserLine = step.text
                    StepKind.TEACHER_LINE -> assertTrue(
                        "$path 出现非脚本台词：${step.text}",
                        step.text == scriptedReply(path, lastUserLine, false) ||
                            step.text == scriptedReply(path, lastUserLine, true),
                    )
                    else -> Unit
                }
            }
        }
        // 关键锚点：正常路第一句与退出路回复与旧版一字不差。
        assertEquals(
            "用 15 分钟写一个项目的三句话：背景、你的决定、结果。剩下 5 分钟读一遍；先只做这一件。",
            scriptFor(DemoPath.NORMAL)[1].text,
        )
        assertEquals(
            "先挑一个最可能被问到的项目，花 15 分钟写下：背景、你做的决定、结果各一句。最后 5 分钟读一遍。",
            scriptFor(DemoPath.NORMAL)[3].text,
        )
        assertEquals(
            "收到，我们现在结束；我不会主动联系你。",
            scriptFor(DemoPath.EXIT)[1].text,
        )
    }

    @Test
    fun `退出场景没有表扬也没有特效`() {
        val exit = scriptFor(DemoPath.EXIT)
        assertFalse("退出场景不得出现表扬", exit.any { it.kind == StepKind.PRAISE })
        assertFalse("退出场景不得送舞台特效", exit.any { it.gesture != null })
        assertTrue("退出场景必须有 END 终态", exit.any { it.kind == StepKind.END })
        assertEquals("END 必须是最后一步", StepKind.END, exit.last().kind)
    }

    @Test
    fun `表扬只在完成具体动作后出现且带特效`() {
        listOf(DemoPath.NORMAL, DemoPath.AMBIGUOUS, DemoPath.EMOTIONAL, DemoPath.MEMORY).forEach { path ->
            val steps = scriptFor(path)
            val praise = steps.filter { it.kind == StepKind.PRAISE }
            assertTrue("$path 至少一次表扬", praise.isNotEmpty())
            praise.forEach { step ->
                assertEquals("$path 表扬要带表情", TeacherExpression.PRAISE, step.expression)
                assertTrue("$path 表扬要带特效 cue", step.gesture in setOf("wave", "pat_head"))
            }
        }
    }

    @Test
    fun `记忆路先复述旧记忆再给撤销后的更正`() {
        val memory = scriptFor(DemoPath.MEMORY)
        val lines = memory.filter { it.kind == StepKind.TEACHER_LINE }.map { it.text }
        assertTrue("要先复述旧的（未更正）记忆", lines[0].contains("我记得面试在周五"))
        assertTrue("更正后的要明说已撤销", lines.any { it.contains("已撤销") })
    }

    @Test
    fun `情绪化场景不确认同事恶意`() {
        scriptFor(DemoPath.EMOTIONAL).filter { it.kind == StepKind.TEACHER_LINE }.forEach { step ->
            assertFalse("不得把『故意』当事实肯定", step.text.contains("他就是故意"))
        }
    }

    @Test
    fun `步骤游标只在范围内前进`() {
        assertEquals(1, nextStep(0, 5))
        assertEquals(5, nextStep(4, 5))
        assertEquals(5, nextStep(5, 5))
        assertEquals(0, nextStep(-1, 5))
    }

    @Test
    fun `只有退出路的 END 步判定为终态`() {
        assertTrue(endsAt(DemoPath.EXIT, scriptFor(DemoPath.EXIT).lastIndex))
        assertFalse(endsAt(DemoPath.NORMAL, scriptFor(DemoPath.NORMAL).lastIndex))
        assertFalse(endsAt(DemoPath.EXIT, 0))
    }
}
