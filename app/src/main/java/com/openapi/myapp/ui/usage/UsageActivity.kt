package com.openapi.myapp.ui.usage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.ks.free1.R
import com.openapi.myapp.data.SessionRecorder
import com.openapi.myapp.data.UsageStats
import com.openapi.myapp.ui.base.ComposeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 用量统计：今天和最近 7 天的已连接时长、流量、连接次数。
 * 数据来自本机的连接会话记录（ConnSession），不上传。
 */
class UsageActivity : AppCompatActivity() {

    private var days by mutableStateOf<Array<UsageStats.Day>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val compose = ComposeView(this)
        compose.setContent { UsageScreen(days, onBack = { finish() }) }
        setContentView(compose)
    }

    override fun onResume() {
        super.onResume()
        // 查库放后台，回到页面时刷新（正在进行的会话时长会变）
        Thread {
            val loaded = SessionRecorder.loadDays(7)
            runOnUiThread { days = loaded }
        }.start()
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, UsageActivity::class.java))
        }
    }
}

private val Bg = ComposeTheme.Background
private val Card = ComposeTheme.Card
private val TextMain = ComposeTheme.TextPrimary
private val TextSub = ComposeTheme.TextSecondary
private val Accent = Color(0xFF6D4AFF)

@Composable
private fun UsageScreen(days: Array<UsageStats.Day>?, onBack: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Card, primary = Accent)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                text = "‹",
                color = TextSub,
                fontSize = 30.sp,
                modifier = Modifier.padding(bottom = 4.dp).clickable(onClick = onBack),
            )
            Text(
                text = stringResource(R.string.usage_title),
                color = TextMain,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            if (days == null) {
                return@Column
            }
            val today = days.last()
            val totalMs = days.sumOf { it.connectedMs }
            val totalBytes = days.sumOf { it.totalBytes() }
            val totalSessions = days.sumOf { it.sessions }
            if (totalSessions == 0 && totalMs == 0L) {
                Text(stringResource(R.string.usage_empty), color = TextSub, fontSize = 15.sp)
                return@Column
            }
            SummaryCard(stringResource(R.string.usage_today), today.connectedMs, today.totalBytes(), today.sessions)
            Spacer(Modifier.height(12.dp))
            SummaryCard(stringResource(R.string.usage_week), totalMs, totalBytes, totalSessions)
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.usage_daily),
                color = TextMain,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            DailyBars(days)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.usage_note), color = TextSub, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SummaryCard(title: String, ms: Long, bytes: Long, sessions: Int) {
    Surface(color = Card, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = TextSub, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(stringResource(R.string.usage_connected_time), UsageStats.formatDuration(ms))
                Stat(stringResource(R.string.usage_traffic), UsageStats.formatBytes(bytes))
                Stat(stringResource(R.string.usage_sessions), sessions.toString())
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextSub, fontSize = 12.sp)
    }
}

@Composable
private fun DailyBars(days: Array<UsageStats.Day>) {
    val max = (days.maxOf { it.connectedMs }).coerceAtLeast(1L)
    val fmt = SimpleDateFormat("MM/dd", Locale.getDefault())
    Surface(color = Card, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth().height(150.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(
                        text = if (d.connectedMs > 0) UsageStats.formatDuration(d.connectedMs) else "",
                        color = TextSub,
                        fontSize = 9.sp,
                    )
                    val frac = d.connectedMs.toFloat() / max
                    // 有数据的天至少画一小截，否则 1 分钟对 10 小时的柱子是 0 像素
                    val h = if (d.connectedMs > 0) (90 * frac).coerceAtLeast(4f) else 2f
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(h.dp)
                            .background(
                                if (d.connectedMs > 0) Accent else TextSub.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp),
                            ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(fmt.format(Date(d.dayStart)), color = TextSub, fontSize = 10.sp)
                }
            }
        }
    }
}
