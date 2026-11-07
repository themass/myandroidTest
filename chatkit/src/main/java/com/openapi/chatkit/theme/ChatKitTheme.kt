package com.openapi.chatkit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 组件库的颜色。App 用自己的品牌色构造一份传给 [ChatKitTheme]，组件库本身不认识 App 的 token。 */
@Immutable
data class ChatKitColors(
    val accent: Color,
    val onAccent: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val userBubble: Color,
    val assistantBubble: Color,
    val bubbleText: Color,
    val secondaryText: Color,
    val icon: Color,
    val divider: Color,
    val error: Color,
)

/** 组件库的尺寸。头像宽度同时决定 footer 的缩进，保证 footer 和气泡对齐。 */
@Immutable
data class ChatKitDimens(
    val avatarSize: Dp = 36.dp,
    val avatarGap: Dp = 8.dp,
    val listHorizontalPadding: Dp = 12.dp,
    val messageSpacing: Dp = 14.dp,
    val bubbleRadius: Dp = 16.dp,
    /** 靠头像一侧的顶角做成小圆角，形成气泡的「尾巴」。 */
    val bubbleTailRadius: Dp = 4.dp,
    val bubblePaddingHorizontal: Dp = 12.dp,
    val bubblePaddingVertical: Dp = 9.dp,
    /** 消息内容最大宽度占屏幕宽度的比例。 */
    val maxContentFraction: Float = 0.72f,
    val footerIconSize: Dp = 18.dp,
    val footerHitSize: Dp = 34.dp,
)

@Immutable
data class ChatKitTypography(
    val body: TextUnit = 16.sp,
    val bodyLineHeight: TextUnit = 24.sp,
    val secondary: TextUnit = 14.sp,
    val caption: TextUnit = 12.sp,
)

object ChatKitDefaults {
    /** 默认配色与旧版对话页一致：白底、灰色助手气泡、绿色用户气泡、黑色文字。 */
    fun colors(
        accent: Color = Color(0xFFC62828),
        onAccent: Color = Color.White,
        background: Color = Color.White,
        surface: Color = Color.White,
        surfaceVariant: Color = Color(0xFFF5F5F5),
        userBubble: Color = Color(0xFF81EE98),
        assistantBubble: Color = Color(0xFFEEEEEE),
        bubbleText: Color = Color.Black,
        secondaryText: Color = Color(0xFF757575),
        icon: Color = Color(0xFF8A8A8A),
        divider: Color = Color(0xFFE0E0E0),
        error: Color = Color(0xFFD32F2F),
    ) = ChatKitColors(
        accent = accent,
        onAccent = onAccent,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        userBubble = userBubble,
        assistantBubble = assistantBubble,
        bubbleText = bubbleText,
        secondaryText = secondaryText,
        icon = icon,
        divider = divider,
        error = error,
    )
}

private val LocalChatKitColors = staticCompositionLocalOf { ChatKitDefaults.colors() }
private val LocalChatKitDimens = staticCompositionLocalOf { ChatKitDimens() }
private val LocalChatKitTypography = staticCompositionLocalOf { ChatKitTypography() }

/** 给组件库提供配色、尺寸和字号。不包也能用，此时用 [ChatKitDefaults] 的默认值。 */
@Composable
fun ChatKitTheme(
    colors: ChatKitColors = ChatKitDefaults.colors(),
    dimens: ChatKitDimens = ChatKitDimens(),
    typography: ChatKitTypography = ChatKitTypography(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalChatKitColors provides colors,
        LocalChatKitDimens provides dimens,
        LocalChatKitTypography provides typography,
        content = content,
    )
}

/** 组件内部读取当前主题的入口。 */
object ChatKit {
    val colors: ChatKitColors
        @Composable @ReadOnlyComposable get() = LocalChatKitColors.current
    val dimens: ChatKitDimens
        @Composable @ReadOnlyComposable get() = LocalChatKitDimens.current
    val typography: ChatKitTypography
        @Composable @ReadOnlyComposable get() = LocalChatKitTypography.current
}
