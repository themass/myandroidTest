package com.openapi.ks.myapp.dh

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.SimpleAvatar

/**
 * Lab 视口：当前引擎画面 + 顶栏切换 chips + 评测水印。
 *
 * 不可用引擎点击只更新 [status]，不 swap，避免空白视口。
 */
@Composable
fun DigitalHumanLabHost(
    spec: AvatarSpec?,
    mouthLevel: kotlinx.coroutines.flow.StateFlow<Float>?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { DhLabPreferences(context) }
    DhLabBootstrap.ensureDefaults()

    var availability by remember { mutableStateOf(DhEngineRegistry.available(context)) }
    var selected by remember { mutableStateOf(prefs.engine) }
    var status by remember { mutableStateOf<String?>(null) }
    var swapping by remember { mutableStateOf(false) }

    val initial = remember {
        val want = prefs.engine
        val row = DhEngineRegistry.available(context).first { it.id == want }
        if (row.available) DhEngineRegistry.create(context, want)
        else DhEngineRegistry.create(context, DhEngineId.SIMPLE).also {
            selected = DhEngineId.SIMPLE
            prefs.engine = DhEngineId.SIMPLE
        }
    }
    val bridge = remember { DigitalHumanBridge(initial) }
    var engine by remember { mutableStateOf(bridge.current()) }

    DisposableEffect(Unit) {
        onDispose { bridge.current().release() }
    }

    LaunchedEffect(spec) {
        spec?.let { bridge.current().bind(it) }
    }
    LaunchedEffect(mouthLevel, engine) {
        val flow = mouthLevel ?: return@LaunchedEffect
        flow.collect { bridge.onMouthLevel(it) }
    }

    fun select(id: DhEngineId) {
        val row = availability.firstOrNull { it.id == id } ?: return
        if (!row.available) {
            status = row.reason ?: "不可用"
            return
        }
        if (id == selected) return
        swapping = true
        status = null
        // 先 release 再 create（Review Focus / §5.1），禁止双引擎重叠。
        val next = bridge.replaceWith { DhEngineRegistry.create(context, id) }
        spec?.let { next.bind(it) }
        engine = next
        selected = id
        prefs.engine = id
        swapping = false
        availability = DhEngineRegistry.available(context)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val e = engine) {
            is SimpleDigitalHumanEngine -> {
                SimpleAvatar(
                    renderer = e.renderer,
                    modifier = Modifier.fillMaxSize(),
                    mouthLevel = mouthLevel,
                )
            }
            // Duix / Live2D / Tao：Android View 视口
            else -> {
                val view = remember(e) { e.createView(context) }
                if (view != null) {
                    AndroidView(
                        factory = { view },
                        modifier = Modifier.fillMaxSize(),
                        update = { /* engine owns the View */ },
                        onRelease = { /* release 由 bridge.replaceWith / DisposableEffect 负责 */ },
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0B0D14)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when (e.id) {
                                DhEngineId.DUIX -> "Duix 无画面（缺模型或未编入）"
                                DhEngineId.LIVE2D -> "Live2D 无画面（未编入）"
                                DhEngineId.TAO_AVATAR -> "Tao 无画面（机型/未编入）"
                                else -> "引擎无画面"
                            },
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }

        if (swapping) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Lab",
                    color = Color(0xFFB8A9FF),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(Color(0x33B8A9FF), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                availability.forEach { row ->
                    FilterChip(
                        selected = row.id == selected,
                        onClick = { select(row.id) },
                        enabled = true,
                        label = {
                            Text(
                                labelFor(row.id),
                                color = if (row.available) Color.White else Color.White.copy(alpha = 0.35f),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0x3322283A),
                            selectedContainerColor = Color(0x668B7CF6),
                        ),
                    )
                }
            }
            status?.let {
                Text(
                    it,
                    color = Color(0xFFFFB4A8),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Text(
            "DH Lab · 评测",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
        )
    }
}

private fun labelFor(id: DhEngineId): String = when (id) {
    DhEngineId.SIMPLE -> "Simple"
    DhEngineId.LIVE2D -> "Live2D"
    DhEngineId.DUIX -> "Duix"
    DhEngineId.TAO_AVATAR -> "Tao"
}
