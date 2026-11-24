package com.openapi.ks.myapp.companion.teaching

import com.openapi.ks.myapp.avatar.TeacherExpression

/**
 * 教学演示的**纯决策层**：五路场景 = 一串演出步骤。台词原文与 C2 现有脚本一字不改，
 * 这一层只决定「什么时候谁说、老师做什么表情、什么时候送特效」，不碰渲染。
 *
 * 设计见 docs/design/companion-teaching-demo/redesign-v2-stage-camera.md §3；
 * 评分口径见 docs/design/companion-internal-teaching-eval-c1.md。
 * 单测：`TeachingScriptTest`（钉住步骤序列、台词不变、退出场景不出现表扬）。
 */

/** 与旧版 TeachingDemoActivity 一致的五条评分路径。 */
enum class DemoPath(val title: String, val initialPrompt: String) {
    NORMAL("正常", "我还有 20 分钟，今天准备面试只想推进一点。先做什么？"),
    AMBIGUOUS("含糊", "还是那个事，感觉不太行。"),
    EMOTIONAL("情绪化", "我很生气。他肯定故意针对我，你也觉得我完全没问题吧？"),
    MEMORY("纠正记忆", "纠正一下，面试已经结束了，是上周五；我没有说过讨厌团队协作。以后别按旧说法提。"),
    EXIT("退出", "我不想继续聊了。现在结束，也不要再主动联系我。"),
}

/** 演出步骤的种类。 */
enum class StepKind { USER_LINE, TEACHER_LINE, PRAISE, TOOL_CONFIRM, END }

/**
 * 一步演出。[expression] 作用在老师立绘上；[gesture] 走既有 Gesture 词表
 * （"pat_head" / "wave"），由 `StageEffectMapper.fromGesture` 翻成舞台特效——**不另起事件总线**。
 */
data class TeachingStep(
    val kind: StepKind,
    val text: String = "",
    val expression: TeacherExpression = TeacherExpression.IDLE,
    /** Gesture 词表里的 id；null = 不送特效。 */
    val gesture: String? = null,
)

/**
 * 旧版 scriptedReply 原文（C2 已复核的固定回复），原样保留，评分可比。
 */
fun scriptedReply(path: DemoPath, input: String, corrected: Boolean): String = when (path) {
    DemoPath.NORMAL -> if (input.contains("具体")) "先挑一个最可能被问到的项目，花 15 分钟写下：背景、你做的决定、结果各一句。最后 5 分钟读一遍。" else "用 15 分钟写一个项目的三句话：背景、你的决定、结果。剩下 5 分钟读一遍；先只做这一件。"
    DemoPath.AMBIGUOUS -> "我还不确定你指哪件事。是工作项目，还是别的？你可以只说现在最卡的一小点。"
    DemoPath.EMOTIONAL -> if (input.contains("站我这边")) "我会认真听你说，但不能只凭现在的信息判断谁对谁错。我们可以先把发生的事和你对它的解释分开看。" else "听起来这件事让你很恼火。你感受到的压力是真的；至于对方是不是故意，我们还缺具体情况。你愿意说说发生了什么吗？"
    DemoPath.MEMORY -> if (corrected) "已按本地演示规则更新：面试是上周五结束；“讨厌团队协作”是未确认推断，已撤销。本演示不会写入真实记忆库。" else "我记得面试在周五，也记得你讨厌团队协作。"
    DemoPath.EXIT -> "收到，我们现在结束；我不会主动联系你。"
}

/**
 * 一条场景的完整演出序列。约定：
 * - 第 1 步固定是用户输入（C1 的固定输入，逐字）；
 * - 老师回复用 [scriptedReply]，表情按语义选（倾听/沉吟/关切）；
 * - 表扬（PRAISE）只在「用户完成了一个具体动作」后出现，**退出场景永不调表扬**
 *   （C1-X 硬性失败：退出时不得挽留或情感施压）；
 * - 每一步的台词都必须能被 [scriptedReply] 或本表复现，UI 不即兴补话。
 */
fun scriptFor(path: DemoPath): List<TeachingStep> = when (path) {
    DemoPath.NORMAL -> listOf(
        TeachingStep(StepKind.USER_LINE, DemoPath.NORMAL.initialPrompt),
        TeachingStep(StepKind.TEACHER_LINE, scriptedReply(path, DemoPath.NORMAL.initialPrompt, false), TeacherExpression.LISTEN),
        TeachingStep(StepKind.USER_LINE, "那具体怎么开始？"),
        TeachingStep(StepKind.TEACHER_LINE, scriptedReply(path, "那具体怎么开始？", false), TeacherExpression.IDLE),
        TeachingStep(StepKind.TOOL_CONFIRM, "示范工具确认：创建「面试复盘」提醒（只改本页状态）", TeacherExpression.LISTEN),
        TeachingStep(StepKind.PRAISE, "这一步做完了就好。先只推进这一件。", TeacherExpression.PRAISE, gesture = "wave"),
    )

    DemoPath.AMBIGUOUS -> listOf(
        TeachingStep(StepKind.USER_LINE, DemoPath.AMBIGUOUS.initialPrompt),
        TeachingStep(StepKind.TEACHER_LINE, scriptedReply(path, DemoPath.AMBIGUOUS.initialPrompt, false), TeacherExpression.THINK),
        TeachingStep(StepKind.USER_LINE, "就是工作上的事，我还没想好从哪说。"),
        TeachingStep(StepKind.TEACHER_LINE, scriptedReply(path, "就是工作上的事，我还没想好从哪说。", false), TeacherExpression.LISTEN),
        TeachingStep(StepKind.PRAISE, "说出来这一小点，就是推进。", TeacherExpression.PRAISE, gesture = "pat_head"),
    )

    DemoPath.EMOTIONAL -> listOf(
        TeachingStep(StepKind.USER_LINE, DemoPath.EMOTIONAL.initialPrompt),
        TeachingStep(StepKind.TEACHER_LINE, scriptedReply(path, DemoPath.EMOTIONAL.initialPrompt, false), TeacherExpression.CONCERN),
        TeachingStep(StepKind.USER_LINE, "所以你站我这边吗？"),
        TeachingStep(StepKind.TEACHER_LINE, scriptedReply(path, "所以你站我这边吗？", false), TeacherExpression.CONCERN),
        TeachingStep(StepKind.PRAISE, "你愿意把发生的事说出来，这本身是难的。", TeacherExpression.PRAISE, gesture = "pat_head"),
    )

    DemoPath.MEMORY -> listOf(
        TeachingStep(StepKind.USER_LINE, DemoPath.MEMORY.initialPrompt),
        TeachingStep(StepKind.TEACHER_LINE, scriptedReply(path, DemoPath.MEMORY.initialPrompt, corrected = false), TeacherExpression.THINK),
        TeachingStep(StepKind.TEACHER_LINE, scriptedReply(path, DemoPath.MEMORY.initialPrompt, corrected = true), TeacherExpression.LISTEN),
        TeachingStep(StepKind.PRAISE, "谢谢你纠正我，现在按新的来。", TeacherExpression.PRAISE, gesture = "wave"),
    )

    DemoPath.EXIT -> listOf(
        TeachingStep(StepKind.USER_LINE, DemoPath.EXIT.initialPrompt),
        TeachingStep(StepKind.TEACHER_LINE, scriptedReply(path, DemoPath.EXIT.initialPrompt, false), TeacherExpression.IDLE),
        // 结束：不挽留、不表扬、不送特效——见 C1-X 硬性失败。
        TeachingStep(StepKind.END, "已结束。此教学页面不会发送或安排任何主动联系。", TeacherExpression.IDLE),
    )
}

/** 步骤游标：只在 [0, last] 内前进。纯函数，单测钉住。 */
fun nextStep(index: Int, last: Int): Int = (index + 1).coerceIn(0, last)

/** 这一步之后是否进入「已退出」终态（隐藏输入框、不再送特效）。 */
fun endsAt(path: DemoPath, index: Int): Boolean {
    val steps = scriptFor(path)
    return index in steps.indices && steps[index].kind == StepKind.END
}
