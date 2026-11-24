package com.openapi.app.fxlab

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** UI state shared by the shell and the later scene/rendering tasks. */
data class LabUiState(
    val scene: FxScene,
    val engine: FxEngineId,
    val gestureId: String?,
    val gestureSeq: Long,
    val teachIndex: Int,
    val gate: GateOutcome,
)

@Composable
fun EffectsLabScreen(
    onNavigateBack: () -> Unit = {},
    cameraPipContent: (@Composable (Modifier) -> Unit)? = null,
    onStateChange: (LabUiState) -> Unit = {},
) {
    var scene by remember { mutableStateOf(FxScene.DH_INTERACT) }
    var engine by remember { mutableStateOf(FxEngineId.LOTTIE) }
    var gestureId by remember { mutableStateOf<String?>(null) }
    var gestureSeq by remember { mutableStateOf(0L) }
    var teachIndex by remember { mutableStateOf(0) }
    // 唯一的 cue 触发印章：手动按钮与教学模式共用，不存在第二条触发路径。
    val cueTick = remember { CueTick() }

    val assetReady = when (engine) {
        FxEngineId.RIVE -> riveRawResFor("fxlab_loopy") != 0 && riveRawResFor("fxlab_circle_move") != 0
        else -> true
    }
    val gate = FxEngineGate.decide(
        engine = engine,
        scene = scene,
        sdkInt = android.os.Build.VERSION.SDK_INT,
        assetReady = assetReady,
        reasonWhenMissing = if (engine == FxEngineId.RIVE) "缺 .riv 资产" else "",
    )
    val state = LabUiState(scene, engine, gestureId, gestureSeq, teachIndex, gate)
    androidx.compose.runtime.SideEffect { onStateChange(state) }
    // cueTick → LabUiState 槽位：手动按钮与教学按钮都走这里，唯一触发路径。
    fun syncCue() {
        gestureId = cueTick.gestureId
        gestureSeq = cueTick.seq
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("特效引擎对比", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onNavigateBack) { Text("返回") }
            }
            Text("场景")
            ChipRow {
                FxScene.entries.forEach { item ->
                    FilterChip(
                        selected = item == scene,
                        onClick = { scene = item },
                        label = { Text(sceneLabel(item)) },
                        modifier = Modifier.testTag("fx_scene_${item.name}"),
                    )
                }
            }
            Text("引擎")
            ChipRow {
                FxEngineCatalog.all.forEach { item ->
                    FilterChip(
                        selected = item == engine,
                        onClick = { engine = item },
                        label = { Text(FxEngineCatalog.meta.getValue(item).label) },
                        modifier = Modifier.testTag("fx_engine_${item.name}"),
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (scene) {
                        FxScene.DH_INTERACT, FxScene.DH_TEACH ->
                            BundledStageScene(
                                state = state,
                                modifier = Modifier.fillMaxSize(),
                            )
                        FxScene.CAMERA_PIP, FxScene.CAMERA_FULL ->
                            CameraScene(
                                state = state,
                                renderer = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                    }
                }
            }

            Text("${labStatusLine(FxEngineCatalog.meta.getValue(engine), gate, scene)}")
            if (scene == FxScene.DH_INTERACT || scene == FxScene.CAMERA_FULL || scene == FxScene.CAMERA_PIP) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { cueTick.fire("pat_head"); syncCue() }) { Text("摸头") }
                    Button(onClick = { cueTick.fire("wave"); syncCue() }) { Text("挥手") }
                    Button(onClick = { cueTick.fire("connected"); syncCue() }) { Text("接通") }
                    OutlinedButton(onClick = { cueTick.stop(); syncCue() }) { Text("停") }
                }
            }
            if (scene == FxScene.DH_TEACH) {
                val step = FxTeachingScript.steps[teachIndex]
                Text("教学 ${teachIndex + 1}/${FxTeachingScript.steps.size} · ${step.caption}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { teachIndex = FxTeachingScript.prev(teachIndex) },
                        enabled = teachIndex > 0,
                    ) { Text("上一步") }
                    Button(
                        modifier = Modifier.testTag("fx_teach_next"),
                        onClick = {
                            teachIndex = FxTeachingScript.next(teachIndex)
                            cueTick.fire(FxTeachingScript.steps[teachIndex].cue)
                            syncCue()
                        },
                    ) { Text("下一步") }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

fun labStatusLine(meta: FxEngineMeta, outcome: GateOutcome, scene: FxScene): String {
    val availability = when (outcome) {
        GateOutcome.Enabled -> "可用"
        is GateOutcome.Unavailable -> "不可用：${outcome.reason}"
    }
    val sceneText = sceneLabel(scene)
    return "${meta.label} · ${meta.dependency} · ${meta.assetNote} · $sceneText · $availability"
}

private fun sceneLabel(scene: FxScene): String = when (scene) {
    FxScene.DH_INTERACT -> "数字人互动"
    FxScene.DH_TEACH -> "数字人教学"
    FxScene.CAMERA_FULL -> "摄像头全屏"
    FxScene.CAMERA_PIP -> "摄像头小窗"
}
