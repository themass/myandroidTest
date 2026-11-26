@file:OptIn(ExperimentalFoundationApi::class)

package com.openapi.chatkit.message.parts

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.openapi.chatkit.R
import com.openapi.chatkit.media.ImageViewer
import com.openapi.chatkit.media.imageModel
import com.openapi.chatkit.model.ImageLayout
import com.openapi.chatkit.model.ImageLoadState
import com.openapi.chatkit.model.ImageVisualStatus
import com.openapi.chatkit.model.MessagePart
import com.openapi.mediaview.MediaCarousel
import com.openapi.mediaview.MediaItem
import com.openapi.mediaview.MediaLoadFailedOverlay
import com.openapi.mediaview.MediaStack
import com.openapi.mediaview.MediaTheme
import com.openapi.mediaview.rememberStatefulImageModel
import com.openapi.chatkit.model.Sender
import com.openapi.chatkit.theme.ChatKit
import java.util.Locale

/** 图片块最终采用的排布方式，由 [imageArrangement] 从「张数 + 策略」算出来。 */
internal enum class ImageArrangement {
    /** 单张按原图比例显示（旧的 168×200 气泡缩略图）。 */
    SingleFit,

    /** 收纳盒（mediaview 的 MediaStack）。 */
    Stack,

    /** 横向轮播（mediaview 的 MediaCarousel）。 */
    Carousel,
}

/**
 * 「张数 + 策略 → 排布方式」的决策表，[ImageGrid] 与测试都以此为准（纯 Kotlin，不碰 Compose）。
 *
 * [ImageLayout.Stack] 一律进收纳盒，不看张数（1 张也是收纳盒顶卡）；
 * [ImageLayout.Auto] 保留旧的按张数分支：1 张按比例、2–4 张收纳盒、5 张及以上轮播。
 */
internal fun imageArrangement(count: Int, layout: ImageLayout): ImageArrangement = when (layout) {
    ImageLayout.Stack -> ImageArrangement.Stack
    ImageLayout.Auto -> when {
        count <= 1 -> ImageArrangement.SingleFit
        count <= 4 -> ImageArrangement.Stack
        else -> ImageArrangement.Carousel
    }
}

/**
 * 一条消息里**附件块与文字块**的排布决策（纯 Kotlin，可测；[MessageContent] 与测试都以此为准）。
 *
 * 之前只有文字进气泡、附件是裸卡片，中间还隔着消息级的间距——用户看「图片+文字」就是两条消息
 * （202611 用户反馈）。现在：
 * - [Merged]：同一消息既有附件（图片/文件/视频/位置）又有文字 → **共用一个气泡**（同底色同圆角），
 *   中间用一条分隔线而不是空气；
 * - [Separate]：任一者缺席 → 各自保持原样（纯附件裸排布、纯文字单气泡），不为了合并而合并。
 *
 * 语音不并入：它本身自带完整气泡（转写内嵌），拆开再拼只会重复信息。
 * 位置并入（A43-6，20261126 用户反馈「位置消息应该是一个整体」）：此前位置不在判定里，
 * 带配文的位置消息走 Separate——位置卡一条、配文一条，看上去是两条消息。现在 [hasLocation]
 * 与附件同等参与合并，位置卡进合并气泡的附件位（出卡皮肤，见 [LocationCard]，防双层底色）。
 * 纯位置消息（无文字）仍是位置气泡单独一条，不回退。
 */
internal enum class AttachmentTextLayout { Separate, Merged }

internal fun attachmentTextLayout(
    hasAttachments: Boolean,
    hasLocation: Boolean,
    hasText: Boolean,
): AttachmentTextLayout =
    if ((hasAttachments || hasLocation) && hasText) AttachmentTextLayout.Merged else AttachmentTextLayout.Separate

/** 气泡形状：四角同一圆角（豆包式，不做「尾巴」）。 */
@Composable
fun bubbleShape(sender: Sender): Shape {
    val r = if (sender == Sender.User) ChatKit.dimens.userBubbleRadius else ChatKit.dimens.assistantBubbleRadius
    return RoundedCornerShape(r)
}

/**
 * 消息大图上的「编辑」能力，由宿主（对话页 ChatRoute）提供；null = 查看器里没有编辑按钮，
 * 行为与此前完全一致（陪伴页这类没有编辑管线的宿主就不该看到按钮）。
 *
 * 为什么走 CompositionLocal 而不是加参数：唯一的调用链 ChatMessageList → ChatMessageItem →
 * ImageGrid 中段在 `ChatMessageList.kt`，那个文件本轮不归改，参数穿不过去；CompositionLocal
 * 沿组合树穿透，宿主不提供就没有变化。
 */
val LocalMessageImageEditHandler = staticCompositionLocalOf<((String) -> Unit)?> { null }

/**
 * 文字气泡：用户蓝底白字，助手浅灰底深灰字。具体文字由 [content] 渲染（助手可用 Markdown）。
 *
 * [attachmentsBleed]（A43-4）：合并气泡里图片块右缘空白过深的修法之一——true 时内层盒只留
 * **纵向**内边距，横向不再压 12dp，附件段贴到气泡内边（出血）；文字与 footer 由调用方/这里
 * 自己补回 [ChatKitDimens.bubblePaddingHorizontal]。默认 false：**逐像素不变**
 * （陪伴页这类不传图集的调用方走的就是默认分支）。
 */
@Composable
fun TextBubble(
    sender: Sender,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    /** 气泡卡片内部、正文下面的 footer（助手最新回复）。 */
    footer: (@Composable () -> Unit)? = null,
    /**
     * 读屏动作（整改清单 P2-16）：与长按菜单同一批操作。
     * 挂在气泡自身节点上，读屏在局部菜单里直接执行，不需要「长按」这个摸不到的手势。
     */
    screenReaderActions: List<CustomAccessibilityAction> = emptyList(),
    /** true = 内容横向出血（仅合并气泡带图片时用；见上方注释）。false 时布局与从前逐像素一致。 */
    attachmentsBleed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = ChatKit.colors
    val dimens = ChatKit.dimens
    val isUser = sender == Sender.User
    Surface(
        color = if (isUser) colors.userBubble else colors.assistantBubble,
        contentColor = if (isUser) colors.userBubbleText else colors.assistantBubbleText,
        shape = bubbleShape(sender),
        modifier = modifier
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .then(
                if (screenReaderActions.isEmpty()) Modifier
                else Modifier.semantics { customActions = screenReaderActions }
            ),
    ) {
        if (attachmentsBleed) {
            // 附件段贴气泡内边；文字/分隔线等由调用方补横向 padding；footer 在这里补回。
            Box(Modifier.padding(vertical = dimens.bubblePaddingVertical)) {
                if (footer == null) {
                    content()
                } else {
                    Column {
                        content()
                        Box(Modifier.padding(horizontal = dimens.bubblePaddingHorizontal)) { footer() }
                    }
                }
            }
        } else {
            Box(Modifier.padding(horizontal = dimens.bubblePaddingHorizontal, vertical = dimens.bubblePaddingVertical)) {
                if (footer == null) {
                    content()
                } else {
                    Column {
                        content()
                        footer()
                    }
                }
            }
        }
    }
}

/** 默认的文字渲染：纯文本，颜色取自所在气泡（Surface 的 contentColor），字号和行高统一。 */
@Composable
fun DefaultMessageText(text: String) {
    Text(
        text = text,
        color = Color.Unspecified,
        fontSize = ChatKit.typography.body,
        lineHeight = ChatKit.typography.bodyLineHeight,
    )
}

/**
 * 助手思考中：气泡里的「正在思考…」文字，透明度缓慢呼吸（约 1.6 秒一个来回）。
 * 比三个跳动的点更接近常见对话产品的等待态；流式输出第一段文字到达后即被替换。
 */
@Composable
fun ThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "thinking-alpha",
    )
    Text(
        text = "正在思考…",
        color = ChatKit.colors.secondaryText.copy(alpha = alpha),
        fontSize = ChatKit.typography.body,
        lineHeight = ChatKit.typography.bodyLineHeight,
    )
}

/**
 * 单张图片在气泡里的尺寸上限。
 *
 * 之前是 220 × 280dp：在 372dp 宽的手机上，一张竖拍照片（3:4）会顶满上限，占掉屏幕近六成宽、
 * 将近半屏高，对话被一张图挤到屏幕外。聊天里的图只是「缩略预览」，点开才是大图，
 * 所以收小：竖图约 150 × 200dp，横图约 168 × 126dp。
 */
private val SINGLE_IMAGE_MAX_WIDTH = 168.dp
private val SINGLE_IMAGE_MAX_HEIGHT = 200.dp
private val SINGLE_IMAGE_MIN = 96.dp

/**
 * 图片块。用哪种排布由 [layout] 和张数一起决定（见 [imageArrangement]）：默认 [ImageLayout.Stack]
 * 一律走收纳盒（mediaview 的 MediaStack，一张也是收纳盒的顶卡）；[ImageLayout.Auto] 保留旧的
 * 按张数分支（单张按原图比例显示，2–4 张收纳盒，5 张及以上横向轮播）。
 * 点击任意一张打开全屏查看器（组件自己管理查看器状态）。
 *
 * 加载失败不再是一块无出路的底色（整改清单 P1-10）：失败的图显示「图片加载失败，点击重试」
 * 占位，整块可点击重试；重试期间显示进度样式。每台设备的换源能力不同：组件先做本地强制重载
 * （换缓存 key 重新解码 / 重新请求原地址），同时把 [onRetryImage] 交给调用方去重新换取远程
 * 地址（签名 URL）；调用方换到新地址后更新来源，来源一变组件会按新地址重新加载。
 */
@Composable
fun ImageGrid(
    sources: List<String>,
    maxWidth: Dp,
    /** 长按某一张图：传它的来源（本地路径或地址）。 */
    onLongPress: (String) -> Unit,
    /** 排布策略；默认一律收纳盒，和陪伴端图片消息同一套样式。 */
    layout: ImageLayout = ImageLayout.Stack,
    /**
     * 参数序（台账 minor）：`modifier` 排在带默认值参数的**最后**（layout 之后），与 Compose
     * 惯例（modifier 是第一个带默认值的参数）不一致。唯一调用方 `ChatMessageItem` 全部用具名实参，
     * 所以这里保持原序、只钉注释：把它前移会让「按位传参」的写法静默换义，而这波不做无测试护航的签名搬家。
     */
    modifier: Modifier = Modifier,
    /** 点击了加载失败的那张图（下标）。组件已做本地重载；调用方若能重新换源就换源。 */
    onRetryImage: (Int) -> Unit = {},
    /**
     * 大图上的「编辑」（传这张图的来源）。null（默认）= 查看器里没有编辑按钮，行为与此前完全一致；
     * 非空时 [ImageGrid] 会**先关掉查看器**再把来源交回（查看器是独立窗口，会盖住编辑页）。
     */
    onEdit: ((String) -> Unit)? = null,
) {
    if (sources.isEmpty()) return
    var viewerStart by remember { mutableStateOf<Int?>(null) }
    val placeholder = ChatKit.colors.surfaceVariant
    // 每张图一个状态机实例。来源一换（例如重新签好了 URL）整组回到 Loading，
    // 迟到的旧回调只会落在已被换掉的状态对象上，不会把新图盖成失败。
    val states = remember(sources) { sources.map { mutableStateOf(ImageLoadState()) } }
    fun update(index: Int, transform: (ImageLoadState) -> ImageLoadState) {
        val slot = states.getOrNull(index) ?: return
        slot.value = transform(slot.value)
    }
    fun retry(index: Int) {
        val slot = states.getOrNull(index) ?: return
        val before = slot.value
        val after = before.onRetryClick()
        if (after === before) return // 不在失败态：没有可重试的东西，也不通知调用方
        slot.value = after
        onRetryImage(index)
    }

    val arrangement = imageArrangement(sources.size, layout)
    when (arrangement) {
        ImageArrangement.SingleFit -> {
            val state by states[0]
            val failed = state.status == ImageVisualStatus.Failed || state.status == ImageVisualStatus.Retrying
            val stateful = rememberStatefulImageModel(imageModel(sources[0]), state.attempt)
            val model: Any? = if (failed && state.status != ImageVisualStatus.Retrying) {
                null // 失败且未点重试：不发请求，避免每次重组都重烧一次必失败的加载
            } else {
                stateful
            }
            Box(
                modifier = modifier
                    .sizeIn(
                        minWidth = SINGLE_IMAGE_MIN,
                        minHeight = SINGLE_IMAGE_MIN,
                        maxWidth = minOf(maxWidth, SINGLE_IMAGE_MAX_WIDTH),
                        maxHeight = SINGLE_IMAGE_MAX_HEIGHT,
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(placeholder)
                    .combinedClickable(
                        onClick = {
                            if (state.status == ImageVisualStatus.Failed) retry(0) else viewerStart = 0
                        },
                        onLongClick = { onLongPress(sources[0]) },
                    ),
            ) {
                AsyncImage(
                    model = model,
                    // 气泡里的图片不是纯装饰：读屏要报得出这里有一张图（整改清单 P2-16）
                    contentDescription = stringResource(R.string.chatkit_image),
                    contentScale = ContentScale.Fit,
                    onSuccess = { update(0) { it.onLoaded() } },
                    onError = { update(0) { it.onFailed() } },
                    modifier = Modifier.fillMaxSize(),
                )
                if (state.status == ImageVisualStatus.Failed || state.status == ImageVisualStatus.Retrying) {
                    MediaLoadFailedOverlay(
                        retrying = state.status == ImageVisualStatus.Retrying,
                        compact = true, // 气泡缩略图最大 168×200dp，用紧凑版图标和文字
                    )
                }
            }
        }
        else -> {
            // 收纳盒 / 轮播（通用媒体组件 :mediaview）：张数决定用哪个的时机已经收进
            // imageArrangement，这里只按算出来的结果分发。
            //
            // 已知偏差（cleanup 项 11，台账已登记为真机项）：**[maxWidth] 在这条路上不起作用**。
            // MediaStack 的卡片尺寸是组件自己的硬值（190×140dp + 14dp 错位），调用方给的
            // maxWidth 只约束了 SingleFit 那一臂，所以窄屏上收纳盒可能比气泡的文字还宽。
            // 只在代码侧钉注释、不改尺寸：卡片尺寸是设计稿定的（设计 §视觉），改它属于视觉决策，
            // 要走真机窗口（372dp 宽的 ADY-AL00 上看）而不是在这波里顺手改。
            // 气泡里的图片不是纯装饰：收纳盒路径也要报得出这里有一张图（整改清单 P2-16，
            // 和上面 SingleFit 分支的 AsyncImage 同一处约定——默认样式换成收纳盒后别把它丢了）。
            // 提到循环外取：mapIndexed 的 lambda 不是 @Composable，stringResource 不能在里头调。
            val imageDescription = stringResource(R.string.chatkit_image)
            val items = sources.mapIndexed { index, source ->
                val state by states[index]
                MediaItem(
                    model = imageModel(source),
                    contentDescription = imageDescription,
                    failed = state.status == ImageVisualStatus.Failed || state.status == ImageVisualStatus.Retrying,
                    retrying = state.status == ImageVisualStatus.Retrying,
                    attempt = state.attempt,
                )
            }
            val onTap: (Int) -> Unit = { index -> viewerStart = index }
            val onLong: (Int) -> Unit = { index -> onLongPress(sources[index]) }
            val theme = if (androidx.compose.foundation.isSystemInDarkTheme()) MediaTheme.Dark else MediaTheme.Light
            Box(modifier) {
                when (arrangement) {
                    ImageArrangement.Carousel -> MediaCarousel(
                        items = items,
                        theme = theme,
                        onClick = onTap,
                        onLongClick = onLong,
                        onRetry = ::retry,
                        onItemError = { index -> update(index) { it.onFailed() } },
                    )
                    else -> MediaStack(
                        items = items,
                        theme = theme,
                        // 「1/3」是给人眼看的进度，读屏换成语义说法（cleanup 项 12）。
                        // 格式化走 stringResource(res, arg…) —— 与 MessageSelection 里
                        // `chatkit_selected_count` 同一写法（复审 minor 4）：以前先取回带 %d
                        // 的模板再 String.format，等于绕过了仓库里「资源参数由 stringResource
                        // 一次填好」的约定，位置参数换语言时容易填错顺序。
                        positionDescription = { current, total ->
                            stringResource(R.string.chatkit_stack_position, current, total)
                        },
                        onClick = onTap,
                        onLongClick = onLong,
                        onRetry = ::retry,
                        onItemError = { index -> update(index) { it.onFailed() } },
                    )
                }
            }
        }
    }

    viewerStart?.let { start ->
        ImageViewer(
            sources = sources,
            startIndex = start,
            onDismiss = { viewerStart = null },
            onRetry = ::retry,
            onLongPress = { source ->
                // 先关掉查看器（它是独立窗口，会挡住菜单），再弹出同一张图的菜单
                viewerStart = null
                onLongPress(source)
            },
            onEdit = onEdit?.let { handler ->
                { source ->
                    // 同上：编辑页必须在查看器关掉之后打开，否则被独立窗口盖住
                    viewerStart = null
                    handler(source)
                }
            },
        )
    }
}

/** 文件卡片：类型色块 + 扩展名、文件名、大小。点击交给调用方打开。 */
@Composable
fun FileCard(
    file: MessagePart.File,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChatKit.colors
    val extension = file.name.substringAfterLast('.', "").uppercase(Locale.US).take(4)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.divider),
        modifier = modifier
            .widthIn(min = 200.dp, max = 260.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = colors.bubbleText,
                    fontSize = ChatKit.typography.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(formatFileSize(file.sizeBytes), color = colors.secondaryText, fontSize = ChatKit.typography.caption)
            }
            Spacer(Modifier.width(10.dp))
            FileBadge(extension = extension)
        }
    }
}

/** 文件类型色块：不同类型不同颜色，上面写扩展名。草稿托盘也复用它。 */
@Composable
fun FileBadge(extension: String, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(fileColor(extension)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            extension.ifBlank { "FILE" },
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

internal fun fileColor(extension: String): Color = when (extension.lowercase(Locale.US)) {
    "pdf" -> Color(0xFFE53935)
    "doc", "docx", "wps" -> Color(0xFF1E88E5)
    "xls", "xlsx", "csv" -> Color(0xFF43A047)
    "ppt", "pptx", "key" -> Color(0xFFFB8C00)
    "zip", "rar", "7z", "tar", "gz" -> Color(0xFF8E24AA)
    "txt", "md", "log", "json", "xml" -> Color(0xFF757575)
    "mp3", "wav", "m4a", "aac", "flac" -> Color(0xFF00897B)
    "mp4", "mov", "mkv", "avi" -> Color(0xFF5E35B1)
    "jpg", "jpeg", "png", "gif", "webp", "heic" -> Color(0xFFD81B60)
    "apk" -> Color(0xFF7CB342)
    else -> Color(0xFF90A4AE)
}

internal fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

/**
 * 视频卡片。**有首帧就走收纳盒口径**（mediaview 的 [MediaStack]，一张也是收纳盒的顶卡），
 * 与图片消息共用同一块卡片皮肤，▶ 角标是 [MediaItem.isVideo] 自带的——不再自画
 * 「缩略图 + 中央播放键 + 卡内时长角标」的第二套视觉（口径：视频复用 MediaStack）。
 *
 * 卡片尺寸同样是 MediaStack 的硬值（190×140dp + 14dp 错位），调用方给的 `maxWidth`
 * 在这条臂上不起作用：与 [ImageGrid] 收纳盒分支登记的偏差（cleanup 项 11）同一件事，
 * 同样只在代码侧钉注释，尺寸是设计稿的事，要走真机窗口。
 *
 * 时长与文件名放收纳盒**下方一行题注**，不做卡内角标：MediaStack 没有时长槽位，
 * 往 mediaview 里加一个是 mediaview 的视觉决策，不该由视频这条线顺手改。
 * 没有首帧（历史侧常态，服务端不产缩略图）退化成与 [FileCard] 同款行卡。
 * 点击把 [MessageAction.OpenVideo] 交给调用方——播放用本机文件还是现签的 URL、
 * 用什么播放器，都是业务侧的事，组件不碰网络也不碰文件。
 */
@Composable
fun VideoCard(
    video: MessagePart.Video,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChatKit.colors
    if (video.thumbnailPath.isNotBlank()) {
        // 资源要在 lambda 外取：MediaStack 的回调不是 @Composable，stringResource 进不去
        val videoDescription = stringResource(R.string.chatkit_video)
        val theme = if (androidx.compose.foundation.isSystemInDarkTheme()) MediaTheme.Dark else MediaTheme.Light
        Column(modifier, horizontalAlignment = Alignment.Start) {
            MediaStack(
                items = listOf(
                    MediaItem(
                        model = imageModel(video.thumbnailPath),
                        isVideo = true,
                        contentDescription = videoDescription,
                    ),
                ),
                theme = theme,
                onClick = { onClick() },
                onLongClick = { onLongPress() },
            )
            // 题注：文件名 + 时长。缺哪个就少一段，两个都缺不占位
            val caption = buildString {
                if (video.name.isNotBlank()) append(video.name)
                if (video.durationSeconds > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(formatDuration(video.durationSeconds))
                }
            }
            if (caption.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    caption,
                    color = colors.secondaryText,
                    fontSize = ChatKit.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        // 历史侧/缩略图缺失：退化成与文件卡片同款的行卡
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.divider),
            modifier = modifier
                .widthIn(min = 200.dp, max = 260.dp)
                .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        video.name,
                        color = colors.bubbleText,
                        fontSize = ChatKit.typography.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (video.durationSeconds > 0) formatDuration(video.durationSeconds) else "",
                        color = colors.secondaryText,
                        fontSize = ChatKit.typography.caption,
                    )
                }
                Spacer(Modifier.width(10.dp))
                FileBadge(extension = "MP4")
            }
        }
    }
}

/**
 * 位置内容的**无气泡皮肤**（A43-6）：标题行 + 坐标两行数字 + 精度标注 + 「在地图打开」。
 *
 * 拆出来是为了让它能进「附件+文字」合并气泡（[attachmentTextLayout] 把 location 计入合并后，
 * 位置卡坐在合并气泡的附件位上）——再套一层 [TextBubble] 就是气泡里套气泡、双层底色。
 * 独立位置消息仍用带气泡的 [LocationBubble]。
 *
 * 组件不画地图也不解地址——按钮点击把回调交给调用方（geo: intent 在业务侧），
 * 所以这里连 Android context 能力都不需要，纯 Compose 就能渲染。
 */
@Composable
fun LocationCardContent(
    location: MessagePart.Location,
    onOpenMap: () -> Unit,
) {
    val colors = ChatKit.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.chatkit_location),
                fontWeight = FontWeight.SemiBold,
                fontSize = ChatKit.typography.body,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude),
            fontSize = ChatKit.typography.secondary,
        )
        location.accuracyMeters?.let { meters ->
            Text(
                stringResource(R.string.chatkit_location_accuracy, meters),
                color = colors.secondaryText,
                fontSize = ChatKit.typography.caption,
            )
        }
        Spacer(Modifier.height(8.dp))
        // 用文本按钮而不是 Button：气泡里的次要操作，颜色跟随气泡 contentColor
        Text(
            stringResource(R.string.chatkit_open_in_map),
            color = ChatKit.colors.accent,
            fontSize = ChatKit.typography.secondary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onOpenMap)
                .padding(vertical = 2.dp),
        )
    }
}

/**
 * 位置气泡：整气泡皮肤（[TextBubble]）+ [LocationCardContent]。
 * 只有**没有文字**的位置消息走这条（带配文的位置消息进合并气泡，见 [attachmentTextLayout]）。
 */
@Composable
fun LocationBubble(
    sender: Sender,
    location: MessagePart.Location,
    onOpenMap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    /** 读屏动作，与文字气泡同一套（挂在可点击的气泡节点上）。 */
    screenReaderActions: List<CustomAccessibilityAction> = emptyList(),
) {
    TextBubble(
        sender = sender,
        onLongPress = onLongPress,
        modifier = modifier.widthIn(min = 220.dp),
        screenReaderActions = screenReaderActions,
    ) {
        LocationCardContent(location = location, onOpenMap = onOpenMap)
    }
}
