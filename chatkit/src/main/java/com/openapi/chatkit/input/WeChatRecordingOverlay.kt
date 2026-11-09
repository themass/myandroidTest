package com.openapi.chatkit.input

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * 微信式录音浮层的容器：用 [Popup] 盖住整个屏幕，把录音状态映射给 [WeChatRecordingView] 去画。
 * 手势（左滑取消、上滑转文字、松手发送）由 [VoicePressBar] 处理，这里只负责显示。
 */
/** 微信式录音浮层。 */
@Composable
internal fun WeChatRecordingOverlay(
    intent: VoiceIntent,
    history: List<Float>,
    liveTranscript: String,
) {
    // 弹出层固定在窗口左上角（默认会跟着输入栏的位置，导致上半部分画到屏幕外，只剩输入栏一块变灰）。
    Popup(
        popupPositionProvider = WindowOriginPosition,
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        WeChatRecordingView(
            intent = when (intent) {
                VoiceIntent.Send -> WeChatIntent.Send
                VoiceIntent.Cancel -> WeChatIntent.Cancel
                VoiceIntent.ToText -> WeChatIntent.ToText
            },
            levels = history,
            liveText = liveTranscript.takeIf { it.isNotBlank() },
        )
    }
}

/** 弹出层始终放在窗口的 (0, 0)，配合 fillMaxSize 铺满整个屏幕。 */
private object WindowOriginPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}
