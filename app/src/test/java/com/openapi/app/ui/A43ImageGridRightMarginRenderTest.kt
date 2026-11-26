package com.openapi.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.openapi.chatkit.message.ChatMessageList
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.ImageLayout
import com.openapi.chatkit.model.MessagePart
import com.openapi.chatkit.model.Sender
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A43-4「图集消息右边距过深」的渲染回归（只修取证钉的①②两处，③ 218dp 错位带留真机窗口定）。
 *
 * 根因（协调会话取证，基线 0e9662e7）：
 * ① 合并气泡内容盒压了气泡级 12dp 内边距，图片块**再**带自己的边 → 卡片右缘到气泡右缘
 *    是双份内边距；修法是含图片的合并气泡让图片段出血（贴气泡内边），文字自己补回 12dp。
 * ② merged 内容 Column 没设 horizontalAlignment（默认 Start）：固定宽 218dp 的收纳盒
 *    钉在左侧，用户消息右侧留一条空白带；修法按 sender 对齐。
 *
 * 断言用**边界**（getBoundsInRoot）而不是 tag 存在性：这一项钉的就是「边距」，
 * 存在性断言观察不到。密度取 @Config 默认（mdpi：1dp=1px），容差 ±2dp。
 * 陪伴页护栏（不传图集逐像素不变）由 `mergedWithoutImagesKeepsTextEdges` 钉文字边缘。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w411dp-h891dp",
)
class A43ImageGridRightMarginRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(item: ChatItem) {
        compose.setContent {
            ChatMessageList(
                items = listOf(item),
                onAction = { _, _ -> },
                modifier = Modifier.size(411.dp, 891.dp),
            )
        }
    }

    /** 4 张图 + 配文的用户合并消息（2–4 张走收纳盒：固定 218dp 宽）。 */
    private fun userImagesWithText() = ChatItem(
        id = "img-1",
        sender = Sender.User,
        parts = listOf(
            MessagePart.Images(List(4) { "file:///sdcard/p$it.jpg" }, layout = ImageLayout.Auto),
            MessagePart.Text("四张图"),
        ),
    )

    // Robolectric 默认 mdpi：1dp = 1px，边界直接按 dp 读。
    // 这里敢量边距（ChatUserAvatarRenderTest 躲几何的理由是字体度量）：
    // 钉的是固定内边距与固定宽收纳盒的边缘，与字体无关。
    // useUnmergedTree：收纳盒/分隔线会被合并进气泡语义节点，合并树里找不到它们的 tag。
    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /**
     * 用户消息：图片段出血 + 按发送者对齐——收纳盒**右缘**贴到气泡右缘（≤2dp）。
     *
     * 注意左缘不钉：气泡比收纳盒宽（分隔线 fillMaxWidth 把气泡撑到 maxWidth），
     * End 对齐下收纳盒左缘距气泡左缘是正常余量。这一项钉的是用户投诉的那条**右边**空白带。
     */
    @Test
    fun imageBleedsToTheRightEdgeForUserMessages() {
        render(userImagesWithText())
        val bubble = bounds("merged-bubble")
        val grid = bounds("merged-image-grid")
        compose.onNodeWithTag("merged-bubble").assertIsDisplayed()
        // 修前这里至少差 12dp（气泡内边距）+ Start 对齐把收纳盒钉左侧的偏差；修后图片贴气泡右内边
        check(bubble.right - grid.right <= 2f) {
            "收纳盒右缘必须贴到气泡右缘（出血+End 对齐），bubble.right=${bubble.right} grid.right=${grid.right}"
        }
    }

    /** 助手消息：同一判定按 Start 对齐——收纳盒左缘贴气泡左缘。 */
    @Test
    fun imageBleedsToTheLeftEdgeForAssistantMessages() {
        render(
            ChatItem(
                id = "img-a",
                sender = Sender.Assistant,
                parts = listOf(
                    MessagePart.Images(List(4) { "file:///sdcard/p$it.jpg" }, layout = ImageLayout.Auto),
                    MessagePart.Text("四张图"),
                ),
            ),
        )
        val bubble = bounds("merged-bubble")
        val grid = bounds("merged-image-grid")
        check(grid.left - bubble.left <= 2f) {
            "助手收纳盒左缘必须贴到气泡左缘（出血+Start 对齐），bubble.left=${bubble.left} grid.left=${grid.left}"
        }
    }

    /** 配文不因出血丢内边距：文字左缘比气泡左缘多出的就是补回的 12dp（±2dp）。 */
    @Test
    fun textKeepsTheBubbleInnerPadding() {
        render(userImagesWithText())
        val bubble = bounds("merged-bubble")
        val divider = bounds("merged-divider")
        // 分隔线与文字同一内缩量：分隔线左缘 = 气泡左缘 + 12dp（出血模式下自己补回）
        check((divider.left - bubble.left - 12f).let { it >= -2f && it <= 2f }) {
            "合并气泡内非图片内容必须补回 12dp 内边距，实测 ${divider.left - bubble.left}"
        }
    }

    /**
     * 陪伴页护栏：不含图片的合并气泡（文件卡+文字）不进出血分支——
     * 内容左缘 = 气泡左缘 + 12dp，与从前逐像素一致。
     */
    @Test
    fun mergedWithoutImagesKeepsTheOldPadding() {
        render(
            ChatItem(
                id = "file-1",
                sender = Sender.Assistant,
                parts = listOf(
                    MessagePart.File("/data/local/a.pdf", "a.pdf", 1024),
                    MessagePart.Text("文件说明"),
                ),
            ),
        )
        val bubble = bounds("merged-bubble")
        val divider = bounds("merged-divider")
        check((divider.left - bubble.left - 12f).let { it >= -2f && it <= 2f }) {
            "不含图片的合并气泡内边距必须不变，实测 ${divider.left - bubble.left}"
        }
    }
}
