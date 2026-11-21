package com.openapi.app.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 电话入口的「待进通话」信号必须是**进程内一次性**的，不能进 savedState。
 *
 * 真机验收踩到的坑（P2-13 后复查）：`callLaunchPending` 以前用 rememberSaveable，
 * 进程在信号置位后、消费前被杀（权限弹窗挂着时被系统回收就是一例），
 * 重启恢复 savedState 时 pending 仍是 true，LaunchedEffect 直接替用户拨了电话——
 * 用户看到的现象是「没点通话却自动进了通话」。
 *
 * savedState 恢复后没有任何上下文知道「这个请求是上一个进程发的」。
 * 一次性信号就该死在进程里（见 HomeScreen 该行的注释），要跨进程重建的是
 * 明确的深链（script_case 那类），不是一枚待办位。
 */
class CallLaunchSignalTest {

    private val root: File = File("..").canonicalFile

    @Test
    fun `call launch pending flag is process-local, not saved across recreation`() {
        val homeScreen = File(
            root,
            "app/src/main/java/com/openapi/app/home/presentation/HomeScreen.kt",
        ).readText()
        val declaration = homeScreen.lines().first { it.contains("callLaunchPending by") }
        assertTrue(
            "callLaunchPending 用 rememberSaveable 会把一次性的进通话请求写进 savedState，" +
                "进程死亡重启后会自动拨电话；必须用 remember（见本测试注释）。当前声明：$declaration",
            declaration.contains("remember {") && !declaration.contains("rememberSaveable"),
        )
    }
}
