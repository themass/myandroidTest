package com.openapi.myapp.ui.user

import com.openapi.myapp.ui.base.ComposeTheme
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.data.local.SplitTunnelingPreferences
import com.openapi.domain.model.SplitTunnelingPolicy
import com.openapi.ks.free1.R

/**
 * 分应用代理设置页。
 * 模式：排除（名单外走 VPN，默认）/ 仅选中（只有名单内走 VPN）。
 * 名单为空时等同于所有应用都走 VPN。修改后下一次连接生效。
 */
class SplitTunnelingActivity : AppCompatActivity() {

    private var mode by mutableStateOf(SplitTunnelingPolicy.Mode.EXCLUDE)
    private var selected by mutableStateOf<Set<String>>(emptySet())
    private var apps by mutableStateOf<List<SplitTunnelingPreferences.AppEntry>>(emptyList())
    private var loading by mutableStateOf(true)
    private var query by mutableStateOf("")
    private val icons = mutableStateMapOf<String, ImageBitmap>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val policy = SplitTunnelingPreferences.getPolicy(this)
        mode = policy.mode
        selected = policy.packages

        val compose = ComposeView(this)
        compose.setContent {
            SplitTunnelingScreen(
                mode = mode,
                selected = selected,
                apps = apps,
                icons = icons,
                loading = loading,
                query = query,
                onMode = { mode = it; save() },
                onQuery = { query = it },
                onToggle = ::toggle,
            )
        }
        setContentView(compose)
        loadApps()
    }

    private fun loadApps() {
        Thread {
            val list = SplitTunnelingPreferences.listInstalledApps(this)
            runOnUiThread {
                apps = list
                loading = false
            }
            for (app in list) {
                val bitmap = runCatching {
                    packageManager.getApplicationIcon(app.packageName).toImageBitmap()
                }.getOrNull() ?: continue
                runOnUiThread { icons[app.packageName] = bitmap }
            }
        }.start()
    }

    private fun toggle(pkg: String) {
        selected = if (pkg in selected) selected - pkg else selected + pkg
        save()
    }

    private fun save() {
        SplitTunnelingPreferences.save(this, SplitTunnelingPolicy.of(mode, selected))
        // 首页下次显示时，如果正在连接，会提示重连
        SplitTunnelingPreferences.markReconnectPending(this)
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, SplitTunnelingActivity::class.java))
        }
    }
}

private fun Drawable.toImageBitmap(size: Int = 96): ImageBitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, size, size)
    draw(canvas)
    return bitmap.asImageBitmap()
}

private val Bg = ComposeTheme.Background
private val Card = ComposeTheme.Card
private val TextMain = ComposeTheme.TextPrimary
private val TextSub = ComposeTheme.TextSecondary
private val Accent = Color(0xFF6D4AFF)

@Composable
private fun SplitTunnelingScreen(
    mode: SplitTunnelingPolicy.Mode,
    selected: Set<String>,
    apps: List<SplitTunnelingPreferences.AppEntry>,
    icons: Map<String, ImageBitmap>,
    loading: Boolean,
    query: String,
    onMode: (SplitTunnelingPolicy.Mode) -> Unit,
    onQuery: (String) -> Unit,
    onToggle: (String) -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Card, primary = Accent)) {
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.split_tunnel_title),
                color = TextMain,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == SplitTunnelingPolicy.Mode.EXCLUDE,
                    onClick = { onMode(SplitTunnelingPolicy.Mode.EXCLUDE) },
                    label = { Text(stringResource(R.string.split_tunnel_mode_exclude)) },
                )
                FilterChip(
                    selected = mode == SplitTunnelingPolicy.Mode.INCLUDE,
                    onClick = { onMode(SplitTunnelingPolicy.Mode.INCLUDE) },
                    label = { Text(stringResource(R.string.split_tunnel_mode_include)) },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (mode == SplitTunnelingPolicy.Mode.EXCLUDE) R.string.split_mode_exclude_desc
                    else R.string.split_mode_include_desc,
                ),
                color = TextSub,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (selected.isEmpty()) stringResource(R.string.split_tunnel_empty_hint)
                else stringResource(R.string.split_tunnel_selected_count, selected.size),
                color = TextSub,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.split_tunnel_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.split_tunnel_hint),
                color = TextSub,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))

            if (loading) {
                Text(stringResource(R.string.split_tunnel_loading), color = TextSub)
            } else {
                val keyword = query.trim()
                val shown = apps.filter {
                    keyword.isEmpty() ||
                        it.label.contains(keyword, ignoreCase = true) ||
                        it.packageName.contains(keyword, ignoreCase = true)
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it.packageName }) { app ->
                        AppRow(app, icons[app.packageName], app.packageName in selected) {
                            onToggle(app.packageName)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: SplitTunnelingPreferences.AppEntry,
    icon: ImageBitmap?,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.label, color = TextMain, fontSize = 15.sp)
            Text(text = app.packageName, color = TextSub, fontSize = 12.sp)
        }
        Checkbox(checked = checked, onCheckedChange = { onClick() })
    }
}
