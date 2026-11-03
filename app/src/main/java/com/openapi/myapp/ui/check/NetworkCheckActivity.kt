package com.openapi.myapp.ui.check

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.ks.free1.R
import java.io.IOException

/**
 * 网络体检页：查询出口 IP 的归属地，并和最近连接的节点地址对比。
 * 检测结果会同步到首页地球（紫色标记）。
 */
class NetworkCheckActivity : AppCompatActivity() {

    private var running by mutableStateOf(false)
    private var result by mutableStateOf<NetworkCheck.Result?>(null)
    private var failed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val compose = ComposeView(this)
        compose.setContent {
            NetworkCheckScreen(running, result, failed, onRun = ::runCheck)
        }
        setContentView(compose)
        runCheck()
    }

    private fun runCheck() {
        if (running) return
        running = true
        failed = false
        Thread {
            var checked: NetworkCheck.Result? = null
            var error = false
            try {
                checked = NetworkCheck.run(applicationContext)
            } catch (e: IOException) {
                error = true
            }
            runOnUiThread {
                result = checked
                failed = error
                running = false
            }
        }.start()
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, NetworkCheckActivity::class.java))
        }
    }
}

private val Bg = Color(0xFF1C1B24)
private val Card = Color(0xFF292733)
private val TextMain = Color(0xFFE8E8EE)
private val TextSub = Color(0xFFA7A4B5)
private val Accent = Color(0xFF6D4AFF)
private val Good = Color(0xFF34C759)
private val Bad = Color(0xFFFF9F0A)

@Composable
private fun NetworkCheckScreen(
    running: Boolean,
    result: NetworkCheck.Result?,
    failed: Boolean,
    onRun: () -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Card, primary = Accent)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                text = stringResource(R.string.netcheck_title),
                color = TextMain,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRun, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (running) R.string.netcheck_running else R.string.netcheck_start))
            }
            Spacer(Modifier.height(16.dp))
            if (failed) {
                Text(stringResource(R.string.netcheck_error), color = Bad)
            }
            if (result != null) {
                ResultRows(result)
            }
        }
    }
}

@Composable
private fun ResultRows(result: NetworkCheck.Result) {
    val unknown = stringResource(R.string.netcheck_unknown)
    val location = listOfNotNull(result.city, result.country).joinToString(", ").ifEmpty { unknown }
    val nodeIps = if (result.nodeIps.isEmpty()) unknown else result.nodeIps.joinToString(", ")

    InfoLine(stringResource(R.string.netcheck_exit_ip), result.exitIp ?: unknown)
    InfoLine(stringResource(R.string.netcheck_location), location)
    InfoLine(stringResource(R.string.netcheck_node), result.nodeHost ?: stringResource(R.string.netcheck_no_node))
    InfoLine(stringResource(R.string.netcheck_node_ip), nodeIps)
    Spacer(Modifier.height(12.dp))

    val (message, color) = when {
        result.nodeHost == null -> stringResource(R.string.netcheck_no_node) to TextSub
        result.nodeIps.isEmpty() -> stringResource(R.string.netcheck_node_unresolved) to Bad
        result.matched -> stringResource(R.string.netcheck_match) to Good
        else -> stringResource(R.string.netcheck_mismatch) to Bad
    }
    Text(
        text = "${stringResource(R.string.netcheck_result)}: $message",
        color = color,
        fontSize = 15.sp,
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = label, color = TextSub, fontSize = 13.sp)
        Text(text = value, color = TextMain, fontSize = 16.sp)
    }
}
