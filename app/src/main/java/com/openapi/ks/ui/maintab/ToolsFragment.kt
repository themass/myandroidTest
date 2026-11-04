package com.openapi.ks.ui.maintab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.myapp.ui.base.ComposeTheme
import androidx.fragment.app.Fragment
import com.openapi.ks.free1.R
import com.openapi.myapp.ui.check.NetworkCheckActivity
import com.openapi.myapp.ui.user.SplitTunnelingActivity

/** 底部"工具" tab：网络体检、分应用代理等常用工具，首页就能找到。 */
class ToolsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ToolsScreen(
                    onNetworkCheck = { NetworkCheckActivity.start(requireContext()) },
                    onSplitTunneling = { SplitTunnelingActivity.start(requireContext()) },
                )
            }
        }
    }
}

private val ToolsBg = ComposeTheme.Background
private val ToolsCard = ComposeTheme.Card
private val ToolsText = ComposeTheme.TextPrimary
private val ToolsSub = ComposeTheme.TextSecondary
private val ToolsAccent = Color(0xFF6D4AFF)

@Composable
private fun ToolsScreen(onNetworkCheck: () -> Unit, onSplitTunneling: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(background = ToolsBg, surface = ToolsCard, primary = ToolsAccent)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ToolsBg)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                text = stringResource(R.string.tools_title),
                color = ToolsText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            ToolCard(
                title = stringResource(R.string.tools_netcheck),
                desc = stringResource(R.string.tools_netcheck_desc),
                onClick = onNetworkCheck,
            )
            Spacer(Modifier.height(12.dp))
            ToolCard(
                title = stringResource(R.string.tools_split),
                desc = stringResource(R.string.tools_split_desc),
                onClick = onSplitTunneling,
            )
        }
    }
}

@Composable
private fun ToolCard(title: String, desc: String, onClick: () -> Unit) {
    Surface(
        color = ToolsCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = ToolsText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(text = desc, color = ToolsSub, fontSize = 13.sp)
            }
            Text(text = "›", color = ToolsAccent, fontSize = 22.sp)
        }
    }
}
