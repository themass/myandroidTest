package com.openapi.ks.myapp.companion.teaching

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.openapi.ks.myapp.avatar.BundledAvatars
import com.openapi.ks.myapp.avatar.AvatarPackStore
import com.openapi.ks.myapp.avatar.LayeredAvatarPack
import com.openapi.ks.myapp.avatar.TeacherExpression
import com.openapi.ks.myapp.dh.DhEngineId
import com.openapi.ks.myapp.dh.DhEngineRegistry
import com.openapi.ks.myapp.dh.DhLabBootstrap
import com.openapi.ks.myapp.dh.DigitalHumanLabHost
import com.openapi.ks.myapp.dh.OriginalAvatarDemo
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * 教学演示 v2 的 UI 根：老师（自研分层立绘舞台 + 表情 + 特效）＋ 学生（前置摄像头）＋ 导演脚本。
 * 设计见 docs/design/companion-teaching-demo/redesign-v2-stage-camera.md。
 *
 * **摄像头内容由组合根注入**（[studentCamera]）：feature 互不依赖（ModuleGraphTest），
 * `:feature-companion` 不 import `:feature-camera-fx`——与通话页人脸小窗同一条注入路
 * （HomeScreen → CompanionRoute.faceCameraPipContent → DigitalHumanLabHost）。
 * 权限流程（同意→请求→拒绝可恢复）在本 Route 内做，`CameraFxGrantedContent` 自己从不申请权限。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TeachingDemoRoute(
    speak: (String) -> Unit,
    mouthLevel: StateFlow<Float>,
    speechError: StateFlow<String?>,
    onExit: () -> Unit,
    studentCamera: (@Composable (Modifier, praiseActive: Boolean) -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    DhLabBootstrap.ensureDefaults()

    var path by remember { mutableStateOf(DemoPath.NORMAL) }
    var steps by remember { mutableStateOf(scriptFor(DemoPath.NORMAL)) }
    var index by remember { mutableStateOf(-1) }
    var playing by remember { mutableStateOf(false) }
    var expression by remember { mutableStateOf(TeacherExpression.IDLE) }
    var gestureId by remember { mutableStateOf<String?>(null) }
    var gestureSeq by remember { mutableLongStateOf(0L) }
    var draft by remember { mutableStateOf("") }
    var reminderDialog by remember { mutableStateOf(false) }
    var reminderConfirmed by remember { mutableStateOf(false) }
    val freeMessages = remember { mutableStateListOf<Pair<String, Boolean>>() }

    // 引擎切换（保留 Lab 入口）：默认 SIMPLE=自研立绘；切到 LIVE2D 看版本闸/缺素材原因。
    var engineId by remember { mutableStateOf(DhEngineId.SIMPLE) }
    var engineMenu by remember { mutableStateOf(false) }
    var live2dModel by remember { mutableStateOf(OriginalAvatarDemo.MODEL_DIR) }
    var modelNote by remember { mutableStateOf<String?>(null) }

    // 老师形象：内置自研分层立绘（demo-avatar-v1，自有素材）。
    val avatarStore = remember(context) { AvatarPackStore(context) }
    val teacherPack by produceState<LayeredAvatarPack?>(initialValue = null) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            val key = BundledAvatars.DEMO_KEY
            runCatching { BundledAvatars.ensureDemo(context, avatarStore) }
            avatarStore.loadLayered(key)
        }
    }

    // 摄像头边界：先同意（本机实时处理不上传）→ 再请求权限 → 拒绝可重试。
    var cameraConsented by remember { mutableStateOf(false) }
    var cameraDenied by remember { mutableStateOf(false) }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        cameraDenied = !granted
    }

    val ended = index in steps.indices && steps[index].kind == StepKind.END
    // 记忆是否已按演示规则更正：与旧版语义一致——纠正记忆路里发生过一次更正（脚本步或自由输入）即为真。
    var memoryCorrected by remember { mutableStateOf(false) }
    val speechErr by speechError.collectAsState()

    fun applyStep(i: Int) {
        index = i
        val step = steps[i]
        expression = step.expression
        step.gesture?.let {
            gestureId = it
            gestureSeq++
        }
        if (step.kind == StepKind.TOOL_CONFIRM) reminderDialog = true
        if (step.kind == StepKind.TEACHER_LINE || step.kind == StepKind.PRAISE) speak(step.text)
        if (step.kind == StepKind.END) playing = false
    }

    fun reset(next: DemoPath) {
        path = next
        steps = scriptFor(next)
        index = -1
        playing = false
        expression = TeacherExpression.IDLE
        gestureId = null
        reminderConfirmed = false
        memoryCorrected = false
        freeMessages.clear()
        modelNote = null
        applyStep(0)
    }

    fun send(text: String) {
        if (text.isBlank() || ended) return
        freeMessages += (text.trim() to false)
        if (path == DemoPath.MEMORY) memoryCorrected = true
        val reply = scriptedReply(path, text.trim(), memoryCorrected)
        freeMessages += (reply to true)
        expression = TeacherExpression.LISTEN
        speak(reply)
        draft = ""
    }

    LaunchedEffect(Unit) { if (index < 0) applyStep(0) }

    LaunchedEffect(playing) {
        while (playing && index < steps.lastIndex) {
            delay(STEP_DWELL_MS)
            applyStep(index + 1)
        }
        playing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("教学演示 · 林澄") },
                actions = { TextButton(onClick = onExit) { Text("退出") } },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("教学流程 · 本地脚本", fontWeight = FontWeight.Bold)
                    Text(
                        "老师=自研分层立绘（demo-avatar-v1） · 学生=本机前置摄像头，画面只在本机处理、不上传 · 全程不申请麦克风",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("AI 生成内容 · 角色：林澄", style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DemoPath.entries.forEach { item ->
                    FilterChip(selected = path == item, onClick = { reset(item) }, label = { Text(item.title) })
                }
            }

            if (!ended) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { playing = !playing }) { Text(if (playing) "暂停" else "▶ 播放") }
                    OutlinedButton(
                        enabled = index < steps.lastIndex,
                        onClick = { playing = false; applyStep(nextStep(index, steps.lastIndex)) },
                    ) { Text("单步") }
                    OutlinedButton(onClick = { reset(path) }) { Text("重置") }
                    Text("步骤 ${index + 1}/${steps.size}", style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton(onClick = { engineMenu = true }) {
                            Text("引擎：${com.openapi.ks.myapp.dh.dhEngineLabel(engineId)}")
                        }
                        EngineMenu(
                            expanded = engineMenu,
                            current = engineId,
                            onSelect = { engineId = it; engineMenu = false },
                            onOriginalDemo = {
                                engineMenu = false
                                engineId = DhEngineId.LIVE2D
                                modelNote = runCatching { OriginalAvatarDemo.install(context) }
                                    .fold(
                                        { r -> r.fold({ "已安装 $live2dModel（若被拒绝见下行）" }, { "装失败：${it.message}" }) },
                                        { "装失败：${it.message}" },
                                    )
                            },
                            onDismiss = { engineMenu = false },
                        )
                    }
                }
                modelNote?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }

            // ==== 双画面舞台：老师（左）＋ 学生摄像头（右） ====
            Row(
                modifier = Modifier.weight(1.2f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                ) {
                    DigitalHumanLabHost(
                        spec = null,
                        mouthLevel = mouthLevel,
                        engineId = engineId,
                        live2dModel = live2dModel,
                        simpleFallback = teacherPack,
                        teacherExpression = expression,
                        gestureId = gestureId,
                        gestureSeq = gestureSeq,
                        particles = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        "老师 · ${expression.label()} · ${if (teacherPack != null) "自研立绘" else "立绘未装"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(Color.DarkGray, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        studentCamera == null -> StudentLabel("摄像头未接入（debug 注入缺失）")
                        cameraGranted -> studentCamera(Modifier.fillMaxSize(), expression == TeacherExpression.PRAISE)
                        !cameraConsented -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StudentLabel("学生 = 前置摄像头")
                            OutlinedButton(onClick = { cameraConsented = true }) { Text("开启学生摄像头") }
                            Text("画面只在本机实时处理，不上传、不存储", style = MaterialTheme.typography.labelSmall, color = LABEL_ON_DARK)
                        }
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (cameraDenied) StudentLabel("相机权限被拒，可重试")
                            OutlinedButton(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text(if (cameraDenied) "重试相机权限" else "授权相机")
                            }
                        }
                    }
                    Text(
                        "学生 · 本机摄像头",
                        style = MaterialTheme.typography.labelSmall,
                        color = LABEL_ON_DARK,
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    )
                }
            }

            // ==== 步骤表（导演脚本）＋ 自由对话 ====
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(steps.mapIndexed { i, s -> i to s }) { (i, step) ->
                    StepRow(i, index, step, memoryCorrected)
                }
                items(freeMessages) { (text, teacher) -> FreeRow(text, teacher) }
            }

            Text(
                text = if (memoryCorrected) "演示记忆：面试已结束（上周五）；团队协作偏好已撤销" else "演示记忆：面试安排在周五；用户喜欢短步骤",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            speechErr?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            if (reminderConfirmed) Text("已确认（本地演示）", style = MaterialTheme.typography.labelSmall)

            if (!ended) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("自由输入（走同一段本地脚本）") },
                        maxLines = 2,
                    )
                    Button(onClick = { send(draft) }) { Text("发送") }
                }
            } else {
                Text("已结束。此教学页面不会发送或安排任何主动联系。", modifier = Modifier.padding(vertical = 8.dp))
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (cameraConsented && !cameraGranted && !cameraDenied) {
        LaunchedEffect(Unit) { cameraLauncher.launch(Manifest.permission.CAMERA) }
    }

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

private const val STEP_DWELL_MS = 2600L

private val LABEL_ON_DARK = Color(0xB3FFFFFF)

private fun TeacherExpression.label(): String = when (this) {
    TeacherExpression.IDLE -> "待机"
    TeacherExpression.LISTEN -> "倾听"
    TeacherExpression.THINK -> "沉吟"
    TeacherExpression.PRAISE -> "表扬"
    TeacherExpression.CONCERN -> "关切"
}

@Composable
private fun StudentLabel(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
}

@Composable
private fun EngineMenu(
    expanded: Boolean,
    current: DhEngineId,
    onSelect: (DhEngineId) -> Unit,
    onOriginalDemo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val rows = remember { DhEngineRegistry.available(context) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        rows.forEach { row ->
            val detail = row.reason?.let { "（$it）" } ?: ""
            DropdownMenuItem(
                text = { Text("${com.openapi.ks.myapp.dh.dhEngineLabel(row.id)}$detail") },
                onClick = { onSelect(row.id) },
            )
        }
        DropdownMenuItem(
            text = { Text("原创 Live2D（安装后试切，会被版本闸拒绝）") },
            onClick = onOriginalDemo,
        )
    }
}

@Composable
private fun StepRow(i: Int, current: Int, step: TeachingStep, memoryCorrected: Boolean) {
    val state = when {
        i < current -> "✓"
        i == current -> "▶"
        else -> " "
    }
    val who = when (step.kind) {
        StepKind.USER_LINE -> "学生"
        StepKind.TEACHER_LINE -> "林澄"
        StepKind.PRAISE -> "林澄·表扬"
        StepKind.TOOL_CONFIRM -> "工具"
        StepKind.END -> "结束"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (i == current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(state, style = MaterialTheme.typography.labelMedium)
        Column {
            Text(who, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(
                step.text + if (step.gesture != null) "〔特效 ${step.gesture}〕" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            if (step.kind == StepKind.PRAISE) {
                Text("学生侧：贴纸从摸头爱心换成表扬光弧（本机人脸跟随）", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FreeRow(text: String, fromTeacher: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromTeacher) Arrangement.Start else Arrangement.End,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .background(
                    if (fromTeacher) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(10.dp),
                )
                .padding(8.dp),
        ) {
            if (fromTeacher) Text("AI 生成 · 林澄", style = MaterialTheme.typography.labelSmall)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
