package com.openapi.ks.myapp.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 收纳盒里一行的动作。 */
internal enum class DrawerAction { LISTEN_USER, LISTEN_AI, TOGGLE_CAPTIONS, THEME, VOICE }

internal data class DrawerRow(val action: DrawerAction, val label: String, val active: Boolean)

/**
 * 收纳盒里有哪些行（纯函数，JVM 测试钉住）。
 * - 试听两行只在调试包出现（[previewAvailable]），竖放，正在放的那行变「■ 停止」。
 * - 字幕开关始终在：「字幕 · 开 / 关」。
 * - 背景调色在字幕下面：「背景 · 深海 / 自定义 248°」，点开调色弹窗。
 * - 声音行在最后、只读（服务端还没开换音色接口）：服务端下发了音色才出现。
 */
internal fun heroDrawerRows(
    previewAvailable: Boolean,
    previewSpeaker: String?,
    captionsVisible: Boolean,
    theme: CallTheme,
    voiceLabel: String? = null,
): List<DrawerRow> = buildList {
    if (previewAvailable) {
        add(DrawerRow(DrawerAction.LISTEN_USER, if (previewSpeaker == "user") "■ 停止用户试听" else "▶ 用户试听", previewSpeaker == "user"))
        add(DrawerRow(DrawerAction.LISTEN_AI, if (previewSpeaker == "ai") "■ 停止 AI 试听" else "▶ AI 试听", previewSpeaker == "ai"))
    }
    add(DrawerRow(DrawerAction.TOGGLE_CAPTIONS, if (captionsVisible) "字幕 · 开" else "字幕 · 关", captionsVisible))
    val themeLabel = if (theme.id == CallTheme.CUSTOM_ID) {
        val hue = theme.hueDegrees ?: CallTheme.DEFAULT_CUSTOM_HUE
        "背景 · 自定义 ${hue.toInt()}°"
    } else {
        "背景 · ${theme.uiName}"
    }
    add(DrawerRow(DrawerAction.THEME, themeLabel, theme.id == CallTheme.CUSTOM_ID))
    val voice = voiceLabel?.trim()?.takeIf { it.isNotEmpty() }
    if (voice != null) {
        // active=false：当前是只读展示行（点不响应），将来接口开了再变可点。
        add(DrawerRow(DrawerAction.VOICE, "声音 · $voice", active = false))
    }
}

/**
 * 主角模式右侧的「收纳盒」：贴在屏幕右缘的一枚小把手，点开后面板从右缘横向展开
 * （宽度 + 淡入），再点收回。收起时只剩把手，不挡人物。
 */
@Composable
internal fun HeroSideDrawer(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    rows: List<DrawerRow>,
    onRowClick: (DrawerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelShape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
    Row(
        modifier = modifier.height(IntrinsicHeightDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 把手：始终可见。「‹」表示可展开，「›」表示可收起。
        Column(
            modifier = Modifier
                .width(26.dp)
                .height(72.dp)
                .background(HANDLE_BG, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .clickable(onClick = onToggleExpanded)
                .semantics { contentDescription = if (expanded) "收起工具盒" else "展开工具盒" },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (expanded) "›" else "‹", color = TEXT_PRIMARY, fontSize = 20.sp)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(tween(220), expandFrom = Alignment.End) + fadeIn(tween(220)),
            exit = shrinkHorizontally(tween(180), shrinkTowards = Alignment.End) + fadeOut(tween(180)),
        ) {
            Column(
                modifier = Modifier
                    .background(PANEL_BG, panelShape)
                    .border(1.dp, PANEL_BORDER, panelShape)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                rows.forEach { row ->
                    Text(
                        text = row.label,
                        color = if (row.active) TEXT_PRIMARY else TEXT_SECONDARY,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .background(
                                if (row.active) ROW_ACTIVE_BG else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { onRowClick(row.action) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// 4 行（试听×2 + 字幕 + 背景）时每行约 34dp + 8dp 间距 + 20dp 内边距；留到 200 不裁行。
private val IntrinsicHeightDp = 200.dp
private val HANDLE_BG = Color(0x990B1122)
private val PANEL_BG = Color(0xCC0B1122)
private val PANEL_BORDER = Color(0x33FFFFFF)
private val ROW_ACTIVE_BG = Color(0x338B7CF6)
private val TEXT_PRIMARY = Color(0xFFF3F5FF)
private val TEXT_SECONDARY = Color(0xFFA3ADCB)
