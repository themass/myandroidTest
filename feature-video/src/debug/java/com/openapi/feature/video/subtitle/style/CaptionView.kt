package com.openapi.feature.video.subtitle.style

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 通用字幕渲染层：把一句文本按 [CaptionStyle] 画出来，放不下就按 [CaptionMotionMode] 动。
 *
 * 布局逻辑只有一处决策——[planCaptionMotion]（纯函数，JVM 测试钉死）；这里只做三件事：
 * 量文本、照计划推进度、把进度换算成位移。任何页面给一句 cue 和一个样式就能用，
 * 不绑定放映场，也不绑定具体播放器内核。
 */
@Composable
internal fun CaptionOverlay(
    cue: String,
    style: CaptionStyle,
    modifier: Modifier = Modifier,
) {
    if (cue.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fontSp = CaptionStyle.BASE_TEXT_SP * style.fontScale
    val textStyle = TextStyle(
        color = Color(style.colorArgb),
        fontSize = fontSp.sp,
        textAlign = TextAlign.Center,
    )
    val background = Color(0xFF000000).copy(alpha = style.backgroundAlpha)

    BoxWithConstraints(modifier) {
        val viewportPx = with(density) { maxWidth.toPx() }
        val fontPx = with(density) { fontSp.sp.toPx() }
        // 两份量：不限宽的天然尺寸（走字要用），和按可视宽折行后的行数（滚动要比较）
        val flat = measurer.measure(cue, textStyle, constraints = Constraints())
        val wrapped = measurer.measure(
            cue, textStyle,
            constraints = Constraints(maxWidth = viewportPx.toInt()),
        )
        val lineHeightPx = flat.size.height.toFloat()
        val plan = remember(cue, style, viewportPx) {
            planCaptionMotion(
                style.motion, flat.size.width.toFloat(), viewportPx,
                wrapped.lineCount, style.maxLines, lineHeightPx, fontPx,
            )
        }
        // 动画进度：跟着帧时钟走；cue 或计划一换就归零重来
        var progress by remember(cue, plan) { mutableFloatStateOf(0f) }
        LaunchedEffect(cue, plan) {
            progress = 0f
            val duration = when (plan) {
                is CaptionPlan.Marquee -> plan.durationMs
                is CaptionPlan.ScrollUp -> plan.durationMs
                CaptionPlan.Static -> 0L
            }
            if (duration > 0L) {
                var elapsed = 0L
                var last = withFrameNanos { it } / 1_000_000L
                while (elapsed < duration) {
                    val now = withFrameNanos { it } / 1_000_000L
                    elapsed += now - last
                    last = now
                    progress = motionProgress(plan, elapsed)
                }
                progress = motionProgress(plan, duration)
            }
        }
        val lineHeightDp = with(density) { lineHeightPx.toDp() }
        when (plan) {
            is CaptionPlan.Marquee -> {
                // 通栏当底衬；位移从「整行藏在对侧右缘外」到「整行出左缘」
                Box(
                    Modifier.fillMaxWidth().height(lineHeightDp).clipToBounds()
                        .background(background),
                ) {
                    BasicText(
                        cue,
                        style = textStyle.copy(textAlign = TextAlign.Start),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            translationX = viewportPx - plan.travel * progress
                        },
                    )
                }
            }
            is CaptionPlan.ScrollUp -> {
                // 可视高度 = 允许的行数；文本顶对齐进来（先看到第一行），按阅读速度往上滚到末行
                Box(
                    Modifier.fillMaxWidth().height(with(density) { (style.maxLines * lineHeightPx).toDp() })
                        .clipToBounds().background(background),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Box(Modifier.graphicsLayer { translationY = -plan.travel * progress }) {
                        BasicText(cue, modifier = Modifier.fillMaxWidth(), style = textStyle, softWrap = true)
                    }
                }
            }
            CaptionPlan.Static -> {
                Box(
                    Modifier.clip(RoundedCornerShape(5.dp)).background(background)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    BasicText(cue, style = textStyle, maxLines = style.maxLines)
                }
            }
        }
    }
}
