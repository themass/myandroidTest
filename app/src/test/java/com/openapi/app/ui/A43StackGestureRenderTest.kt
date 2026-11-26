package com.openapi.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.openapi.chatkit.message.parts.ImageGrid
import com.openapi.chatkit.model.ImageLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A43-5「旧图集滑动切换失效」的回归（协调会话取证的决定性根因）。
 *
 * 根因（基线 0e9662e7）：mediaview MediaStack 的 front/dragX 是 `remember(items)`，手势协程
 * 却按 `pointerInput(count)` 挂——items 每次重组由 MessageParts 新建，任一卡 failed/retrying/
 * attempt 翻转或签名 URL 重签（历史图 300s 过期→403）都会让 remember **换状态对象**，
 * 而旧手势协程还在读写旧对象：卡片不跟手、front 永不推进，滑动永久失灵（滚出滚回才「复活」）。
 * 修法：状态键改 `remember(count)`——张数真变才回第一张，内容级 churn 绝不重置。
 *
 * 钉法：走 chatkit 的 ImageGrid（:app 测试类路径经 chatkit 到 mediaview，同 StackBadgeA11yRenderTest）：
 * 换一次**内容变化的 sources**（模拟重签换 URL 的 churn），随后一次滑动仍必须推进牌堆角标。
 * 修前必红：remember(items) 换对象，旧手势写旧状态，角标永停「第 1 张」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w411dp-h891dp",
)
class A43StackGestureRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun renderHost() {
        compose.setContent {
            var tick by remember { mutableIntStateOf(0) }
            // tick 换内容：模拟历史签名 URL 重签（来源变→MediaItem 内容变→churn），张数不变
            val sources = remember(tick) {
                List(3) { "file:///sdcard/p$it.jpg?v$tick" }
            }
            Box(Modifier.size(400.dp, 700.dp)) {
                ImageGrid(
                    sources = sources,
                    maxWidth = 400.dp,
                    layout = ImageLayout.Stack,
                    onLongPress = {},
                    modifier = Modifier.testTag("stack-grid"),
                )
                // 触发器放左下角：不与顶卡（218×168dp 从盒顶左起）重叠，避免抢走滑动手势
                Button(onClick = { tick += 1 }, modifier = Modifier.align(Alignment.BottomStart).testTag("churn")) {
                    Text("churn")
                }
            }
        }
    }

    /** 顶卡横向拖过阈值（190dp × 0.22 ≈ 42px，Robolectric mdpi 1dp=1px）：牌堆必须前进一格。 */
    private fun swipeStack() {
        compose.onNodeWithTag("stack-grid").performTouchInput {
            swipe(
                start = Offset(center.x - 60f, center.y),
                end = Offset(center.x + 80f, center.y),
                durationMillis = 120,
            )
        }
        compose.waitForIdle()
    }

    /** 基线：无 churn 时滑动推进牌堆（证明手势在测试环境本身可用）。 */
    @Test
    fun swipeAdvancesTheStack() {
        renderHost()
        // 角标的读屏说法在语义里是 contentDescription（clearAndSetSemantics 换掉「1/3」字面，
        // StackBadgeA11yRenderTest 同口径）
        compose.onNodeWithContentDescription("第 1 张，共 3 张").assertExists()
        swipeStack()
        compose.onNodeWithContentDescription("第 2 张，共 3 张").assertExists()
    }

    /**
     * 回归钉：滑动推进之后，来源换了（churn：等值但新实例的 items），牌堆位置**必须保住**。
     * 修前必红：front/dragX 是 remember(items)——churn 重建状态对象回第 1 张，
     * 而 pointerInput(count) 的旧手势协程还在读写旧对象（用户侧即「旧的滑不动」）。
     */
    @Test
    fun churnDoesNotResetStackPosition() {
        renderHost()
        swipeStack()
        compose.onNodeWithContentDescription("第 2 张，共 3 张").assertExists()
        compose.onNodeWithTag("churn").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("第 2 张，共 3 张").assertExists()
    }
}
