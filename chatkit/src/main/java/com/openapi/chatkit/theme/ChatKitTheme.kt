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
    /** 用户气泡里的文字（豆包：白字）。 */
    val userBubbleText: Color,
    /** 助手气泡里的文字（豆包：#333333）。 */
    val assistantBubbleText: Color,
    /** 助手 footer 芯片的底色与图标色。 */
    val chipBackground: Color,
    val chipIcon: Color,
    /** 长按菜单（底部弹层）的底色。 */
    val menuBackground: Color,
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
    /** 消息左右留白（用户要求 2dp：原来 10dp 加上气泡外边距，两侧太空）。 */
    val listHorizontalPadding: Dp = 2.dp,
    val messageSpacing: Dp = 14.dp,
    /** 气泡四角圆角（第 0 步在豆包上实测：用户约 14dp，助手约 12dp）。 */
    val userBubbleRadius: Dp = 14.dp,
    val assistantBubbleRadius: Dp = 12.dp,
    /** 气泡内边距（实测文字离气泡左边约 15–16dp，上下约 12dp）。 */
    val bubblePaddingHorizontal: Dp = 12.dp,
    val bubblePaddingVertical: Dp = 10.dp,
    /** 消息内容最大宽度占屏幕宽度的比例。 */
    /** 用户气泡最大宽度占屏宽的比例（75%，见 SPEC2b）。 */
    val maxContentFraction: Float = 0.75f,
    /** 助手气泡最大宽度占屏宽的比例（88%），两侧另有 [listHorizontalPadding] 留白。 */
    val assistantMaxContentFraction: Float = 0.9f,
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
    /** 默认配色：暖橙主色、浅暖用户气泡（#7C2D12 字）、浅灰助手气泡。App 可以用自己的 token 覆盖。 */
    fun colors(
        accent: Color = Color(0xFFC2410C),
        onAccent: Color = Color.White,
        background: Color = Color.White,
        surface: Color = Color.White,
        surfaceVariant: Color = Color(0xFFF5F5F5),
        userBubble: Color = Color(0xFFFDE7D3),
        assistantBubble: Color = Color(0xFFF5F5F5),
        userBubbleText: Color = Color(0xFF7C2D12),
        assistantBubbleText: Color = Color.Black,
        chipBackground: Color = Color(0xFFFDE7D3),
        chipIcon: Color = Color(0xFFC2410C),
        menuBackground: Color = Color(0xFFFFF8F3),
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
        userBubbleText = userBubbleText,
        assistantBubbleText = assistantBubbleText,
        chipBackground = chipBackground,
        chipIcon = chipIcon,
        menuBackground = menuBackground,
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
