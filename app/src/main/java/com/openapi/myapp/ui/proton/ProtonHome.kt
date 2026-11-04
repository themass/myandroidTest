package com.openapi.myapp.ui.proton

import com.openapi.myapp.ui.base.ComposeTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.ks.free1.R

/** 连接状态在 ProtonVPN 风格首页里的四种显示。 */
enum class ProtonVpnUi { Idle, Connecting, Connected, Error }

/**
 * 首页状态。VpnRadFragment 在状态变化时写入，Compose 界面读取并自动重组。
 */
class ProtonHomeState {
    var vpnState by mutableStateOf(ProtonVpnUi.Idle)
    var serverName by mutableStateOf("")
    var pingText by mutableStateOf("")
    var flagRes by mutableStateOf<Int?>(null)
}

private val ProtonBg = ComposeTheme.Background
private val ProtonCard = ComposeTheme.Card
private val ProtonTextPrimary = ComposeTheme.TextPrimary
private val ProtonTextSecondary = ComposeTheme.TextSecondary
private val ProtonGreen = Color(0xFF1C9C7C)
private val ProtonAlert = Color(0xFFDC3251)
private val ProtonAccent = Color(0xFF6D4AFF)
private val ProtonAmber = Color(0xFFFF9900)

/** 供 Java 调用：把 Compose 首页绑定到 ComposeView。 */
fun bindProtonHome(
    view: ComposeView,
    state: ProtonHomeState,
    onConnect: Runnable,
    onServers: Runnable,
) {
    view.setContent {
        ProtonHomeScreen(
            state = state,
            onConnect = { onConnect.run() },
            onServers = { onServers.run() },
        )
    }
}

@Composable
fun ProtonHomeScreen(state: ProtonHomeState, onConnect: () -> Unit, onServers: () -> Unit) {
    val title = when (state.vpnState) {
        ProtonVpnUi.Idle -> stringResource(R.string.proton_status_unprotected)
        ProtonVpnUi.Connecting -> stringResource(R.string.proton_status_connecting)
        ProtonVpnUi.Connected -> stringResource(R.string.proton_status_protected)
        ProtonVpnUi.Error -> stringResource(R.string.proton_status_failed)
    }
    val titleColor = when (state.vpnState) {
        ProtonVpnUi.Idle -> ProtonTextSecondary
        ProtonVpnUi.Connecting -> ProtonAmber
        ProtonVpnUi.Connected -> ProtonGreen
        ProtonVpnUi.Error -> ProtonAlert
    }
    val buttonColor = when (state.vpnState) {
        ProtonVpnUi.Idle -> ProtonAccent
        ProtonVpnUi.Connecting -> ProtonAmber
        ProtonVpnUi.Connected -> ProtonGreen
        ProtonVpnUi.Error -> ProtonAlert
    }
    val buttonLabel = when (state.vpnState) {
        ProtonVpnUi.Idle -> stringResource(R.string.proton_btn_connect)
        ProtonVpnUi.Connecting -> stringResource(R.string.proton_btn_cancel)
        ProtonVpnUi.Connected -> stringResource(R.string.proton_btn_disconnect)
        ProtonVpnUi.Error -> stringResource(R.string.proton_btn_retry)
    }

    MaterialTheme(colorScheme = darkColorScheme(background = ProtonBg, surface = ProtonCard, primary = ProtonAccent)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ProtonBg)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(text = title, color = titleColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(buttonColor)
                    .clickable(onClick = onConnect),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = buttonLabel, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ProtonCard)
                    .clickable(onClick = onServers)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    state.flagRes?.let { flag ->
                        Image(
                            painter = painterResource(flag),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 28.dp, height = 20.dp).clip(RoundedCornerShape(3.dp)),
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                Column {
                    Text(text = stringResource(R.string.proton_current_server), color = ProtonTextSecondary, fontSize = 12.sp)
                    Text(
                        text = state.serverName.ifEmpty { stringResource(R.string.current_node_none) },
                        color = ProtonTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                }
                Text(text = state.pingText, color = ProtonTextSecondary, fontSize = 14.sp)
            }
        }
    }
}
