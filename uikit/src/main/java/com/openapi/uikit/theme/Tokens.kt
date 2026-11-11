package com.openapi.uikit.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * 颜色。
 *
 * 取值来自现有界面里实际在用的颜色（app/src/main/res/values/colors.xml 与
 * 对话页的硬编码色），而不是凭感觉另起一套——设计系统的第一职责是**收敛**，
 * 其次才是美化。新增颜色前先问：现有 token 里真的没有能用的吗？
 */
object AppColors {

    // 品牌：暖陪伴（暖橙），见 /tmp/doubao-spec/COLOR-warm.md；白字对比度 5.18
    val Primary = Color(0xFFC2410C)          // colorPrimary（style_color_primary）
    val PrimaryDark = Color(0xFF9A3412)      // 按下态 / colorPrimaryDark
    val Secondary = Color(0xFFF2A65A)        // 图标、进度、录音强调

    // 对话：暖白页面，白色卡片有边界；助手气泡浅灰，用户气泡暖色
    val ChatBackground = Color(0xFFFBF8F5)
    val ChatIncoming = Color(0xFFF5F5F5)     // 助手气泡
    val ChatOutgoing = Color(0xFFFDE7D3)     // 用户气泡；助手 footer 芯片的底色也用它
    val ChatOutgoingText = Color(0xFF7C2D12) // 用户气泡文字，对比度 7.83
    val MenuSurface = Color(0xFFFFF8F3)      // 长按菜单底部弹层的暖白底色
    val ChatTimestamp = Color(0xAE858585)    // gray_dark_transparent

    // 中性
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFF5F5F5)
    val TextPrimary = Color(0xFF212121)
    val TextSecondary = Color(0xFF5B6470)    // 在 #F6F8F6 上对比度 5.62
    val Divider = Color(0xFFE0E0E0)

    // 状态
    val Error = Color(0xFFD32F2F)
    val Warning = Color(0xFFF57C00)
    val Success = Color(0xFF388E3C)
    val SuccessContainer = Color(0xFFE3F5E6) // 成功结果卡片底色（拍照解题的答案卡）

    // 视频/播控遮罩
    val Scrim = Color(0x99000000)
    val VideoControls = Color(0xCC000000)
}

/**
 * 间距。
 *
 * 4dp 基准网格。现有布局里散落的 6dp/10dp/14dp 之类在迁移时向下取整到网格，
 * 不是为了"好看"，而是让页面之间不再出现肉眼可见的对不齐。
 */
object AppSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** 页面左右留白，全站统一。 */
    val screenHorizontal: Dp = 16.dp

    /** 列表项之间的最小间隔。 */
    val listGap: Dp = 8.dp
}

/** 形状。 */
object AppShapes {
    val bubble: Dp = 16.dp
    val card: Dp = 12.dp
    val sheet: Dp = 20.dp
    val button: Dp = 24.dp

    /** 对话气泡内侧留白——比通用 padding 略大，文字才不贴边。 */
    val bubblePaddingHorizontal: Dp = 12.dp
    val bubblePaddingVertical: Dp = 8.dp

    /** 气泡最大宽度占屏幕比例，超过就该换行。 */
    const val BUBBLE_MAX_WIDTH_FRACTION = 0.75f
}

/** 排版。 */
object AppTypography {
    val screenTitle: TextUnit = 18.sp
    val sectionTitle: TextUnit = 16.sp
    val body: TextUnit = 15.sp
    val bodyLarge: TextUnit = 16.sp
    val caption: TextUnit = 12.sp
    val timestamp: TextUnit = 11.sp

    val bodyWeight: FontWeight = FontWeight.Normal
    val emphasisWeight: FontWeight = FontWeight.Medium
    val titleWeight: FontWeight = FontWeight.Bold

    /** 正文行高倍数：中文需要比英文更宽松，否则多行会挤。 */
    const val BODY_LINE_HEIGHT_MULTIPLIER = 1.45f

    /** 系统字号被放大时正文的上限，超过这个值布局会崩。 */
    val maxBodySize: TextUnit = 22.sp
}

/** 动效时长。 */
object AppMotion {
    const val fastMs = 150
    const val normalMs = 250
    const val slowMs = 400

    /** 打字机效果每次追加的字数；太大看起来像整段蹦出来。 */
    const val TYPEWRITER_CHARS_PER_TICK = 2
    const val TYPEWRITER_TICK_MS = 18
}
