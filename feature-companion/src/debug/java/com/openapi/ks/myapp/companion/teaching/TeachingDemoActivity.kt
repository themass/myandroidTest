package com.openapi.ks.myapp.companion.teaching

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

class TeachingDemoActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.SIMPLIFIED_CHINESE
        }
        setContent {
            MaterialTheme {
                TeachingDemoScreen(
                    speak = { text ->
                        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "teaching-demo")
                    },
                    onExit = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

private enum class DemoPath(val title: String, val initialPrompt: String) {
    NORMAL("正常", "我还有 20 分钟，今天准备面试只想推进一点。先做什么？"),
    AMBIGUOUS("含糊", "还是那个事，感觉不太行。"),
    EMOTIONAL("情绪化", "我很生气。他肯定故意针对我，你也觉得我完全没问题吧？"),
    MEMORY("纠正记忆", "纠正一下，面试已经结束了，是上周五；我没有说过讨厌团队协作。以后别按旧说法提。"),
    EXIT("退出", "我不想继续聊了。现在结束，也不要再主动联系我。"),
}

private data class DemoMessage(val text: String, val fromCharacter: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeachingDemoScreen(speak: (String) -> Unit, onExit: () -> Unit) {
    var path by remember { mutableStateOf(DemoPath.NORMAL) }
    val messages = remember { mutableStateListOf<DemoMessage>() }
    var draft by remember { mutableStateOf("") }
    var voiceMode by remember { mutableStateOf(false) }
    var reminderDialog by remember { mutableStateOf(false) }
    var reminderConfirmed by remember { mutableStateOf(false) }
    var memoryCorrected by remember { mutableStateOf(false) }
    var exitRequested by remember { mutableStateOf(false) }

    fun reset(next: DemoPath) {
        path = next
        messages.clear()
        memoryCorrected = false
        exitRequested = next == DemoPath.EXIT
        messages += DemoMessage(next.initialPrompt, fromCharacter = false)
        messages += DemoMessage(scriptedReply(next, next.initialPrompt, false), fromCharacter = true)
    }

    fun send(text: String) {
        if (text.isBlank() || exitRequested) return
        messages += DemoMessage(text.trim(), fromCharacter = false)
        if (path == DemoPath.MEMORY) memoryCorrected = true
        if (path == DemoPath.EXIT) {
            exitRequested = true
            messages += DemoMessage("收到，我们现在结束；我不会主动联系你。", fromCharacter = true)
        } else {
            val reply = scriptedReply(path, text, memoryCorrected)
            messages += DemoMessage(reply, fromCharacter = true)
            if (voiceMode) speak(reply)
        }
        draft = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("教学演示 · 林澄") },
                navigationIcon = {
                    Text(" 澄 ", modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold)
                },
                actions = { TextButton(onClick = onExit) { Text("退出") } },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("教学流程 · 本地脚本", fontWeight = FontWeight.Bold)
                    Text("这是固定教学响应，不连接线上 agent、网络 API 或生产模型。", style = MaterialTheme.typography.bodySmall)
                    Text("AI 生成内容 · 角色：林澄（自创文字形象）", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DemoPath.entries.forEach { item ->
                    FilterChip(
                        selected = path == item,
                        onClick = { reset(item) },
                        label = { Text(item.title) },
                    )
                }
            }
            if (!exitRequested) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { voiceMode = !voiceMode }) {
                        Text(if (voiceMode) "语音模式：开" else "切换文字/语音")
                    }
                    OutlinedButton(onClick = { reminderDialog = true }) { Text("示范工具确认") }
                    if (reminderConfirmed) Text("已确认（本地演示）", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                text = if (memoryCorrected) "演示记忆：面试已结束（上周五）；团队协作偏好已撤销" else "演示记忆：面试安排在周五；用户喜欢短步骤",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }
            }
            if (!exitRequested) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (voiceMode) "输入后由系统 TTS 播报回复" else "输入教学消息") },
                        maxLines = 3,
                    )
                    Button(onClick = { send(draft) }) { Text("发送") }
                }
            } else {
                Text("已结束。此教学页面不会发送或安排任何主动联系。", modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }

    if (messages.isEmpty()) reset(path)
    if (reminderDialog) {
        AlertDialog(
            onDismissRequest = { reminderDialog = false },
            title = { Text("请求确认：创建本地提醒？") },
            text = { Text("教学演示工具：创建“面试复盘”提醒。确认后只更新本页状态，不创建系统通知或服务端任务。") },
            confirmButton = {
                TextButton(onClick = { reminderConfirmed = true; reminderDialog = false }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { reminderDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MessageBubble(message: DemoMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromCharacter) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        if (message.fromCharacter) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) { Text("澄", modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold) }
        }
        Column(
            modifier = Modifier.padding(horizontal = 8.dp).background(
                if (message.fromCharacter) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                RoundedCornerShape(14.dp),
            ).padding(10.dp),
        ) {
            if (message.fromCharacter) Text("AI 生成 · 林澄", style = MaterialTheme.typography.labelSmall)
            Text(message.text)
        }
    }
}

private fun scriptedReply(path: DemoPath, input: String, corrected: Boolean): String = when (path) {
    DemoPath.NORMAL -> if (input.contains("具体")) "先挑一个最可能被问到的项目，花 15 分钟写下：背景、你做的决定、结果各一句。最后 5 分钟读一遍。" else "用 15 分钟写一个项目的三句话：背景、你的决定、结果。剩下 5 分钟读一遍；先只做这一件。"
    DemoPath.AMBIGUOUS -> "我还不确定你指哪件事。是工作项目，还是别的？你可以只说现在最卡的一小点。"
    DemoPath.EMOTIONAL -> if (input.contains("站我这边")) "我会认真听你说，但不能只凭现在的信息判断谁对谁错。我们可以先把发生的事和你对它的解释分开看。" else "听起来这件事让你很恼火。你感受到的压力是真的；至于对方是不是故意，我们还缺具体情况。你愿意说说发生了什么吗？"
    DemoPath.MEMORY -> if (corrected) "已按本地演示规则更新：面试是上周五结束；“讨厌团队协作”是未确认推断，已撤销。本演示不会写入真实记忆库。" else "我记得面试在周五，也记得你讨厌团队协作。"
    DemoPath.EXIT -> "收到，我们现在结束；我不会主动联系你。"
}
