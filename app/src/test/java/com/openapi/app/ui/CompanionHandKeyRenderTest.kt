package com.openapi.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.openapi.ks.myapp.companion.CallControlBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 通话页底栏的常驻举手键（C7）：真的把 [CallControlBar] 组合出来，按态断言标签与点击。
 *
 * 「打断是一等公民」的第一条证据就是这根键**在界面上可达**——不是只在协议层。
 * 渲染的是底栏本身而不是整页：通话页背景/呼吸环是常开动画，Robolectric 在整页下
 * 等不到 idle（AppNotIdleException，实测）；底栏自己没有动画，契约（控件在不在、
 * 按下去通不通）也只在底栏这一条上。放在 :app 的口径与 CompanionMessageAreaRenderTest
 * 相同（库模块清单合并问题）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w411dp-h891dp",
)
class CompanionHandKeyRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(speaking: Boolean, onRaiseHand: () -> Unit = {}) {
        compose.setContent {
            CallControlBar(
                isMuted = false,
                cameraActive = false,
                cameraAvailable = false,
                composerOpen = false,
                agentSpeaking = speaking,
                onToggleMute = {},
                onToggleCamera = {},
                onOpenComposer = {},
                onRaiseHand = onRaiseHand,
                onHangUp = {},
            )
        }
    }

    @Test
    fun `the hand key is on the bar and says nudge when idle`() {
        render(speaking = false)
        compose.onNodeWithText("叫她说话").assertIsDisplayed()
    }

    @Test
    fun `while she is speaking the same key says interrupt`() {
        render(speaking = true)
        compose.onNodeWithText("打断").assertIsDisplayed()
        compose.onNodeWithText("叫她说话").assertDoesNotExist()
    }

    @Test
    fun `pressing the hand key fires the callback`() {
        var presses = 0
        render(speaking = false, onRaiseHand = { presses++ })
        compose.onNodeWithContentDescription("叫她说话").performClick()
        assertEquals(1, presses)
    }

    @Test
    fun `the old controls stay reachable next to the new key`() {
        // 「不改原 UI 结构只补控件」：静音/文字/挂断一个都不能少。
        render(speaking = false)
        compose.onNodeWithText("静音").assertIsDisplayed()
        compose.onNodeWithText("文字").assertIsDisplayed()
        // 挂断是二次确认按钮，初始态文案是「挂断」。
        compose.onNodeWithText("挂断").assertIsDisplayed()
    }
}
