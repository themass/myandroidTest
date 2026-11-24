package com.openapi.app.fxlab

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.openapi.feature.camerafx.CameraFxStickerRenderer
import com.openapi.feature.camerafx.StickerPlacementPx
import com.openapi.feature.companion.fx.LottieFxLayer

/** Which renderer a scene gets for an engine — kept beside the engine enum for review. */
enum class RendererKind { LOTTIE_LAYER, RIVE, GL, AGSL, THREE }

fun stageRendererFor(engine: FxEngineId): RendererKind = when (engine) {
    FxEngineId.LOTTIE -> RendererKind.LOTTIE_LAYER
    FxEngineId.RIVE -> RendererKind.RIVE
    FxEngineId.GL_PARTICLE -> RendererKind.GL
    FxEngineId.THREE_WEBGL -> RendererKind.THREE
    FxEngineId.AGSL_SHADER -> RendererKind.AGSL
}

/** Gate first: an Unavailable engine shows status text, never another engine's pixels. */
fun rendererKindIfEnabled(engine: FxEngineId, gate: GateOutcome): RendererKind? =
    if (gate == GateOutcome.Enabled) stageRendererFor(engine) else null

/**
 * The single value every stateful renderer keys on. Because every stop bumps [stopSeq],
 * a re-trigger of the same cue after a stop always produces a new key → restart.
 */
fun replayTick(triggerSeq: Long, stopSeq: Long): Long = triggerSeq * 1_000_000L + stopSeq

/**
 * After a 停 with no new trigger, nothing may keep running: an un-stopped backend
 * (ambient GL, breathing AGSL, looping rAF) would leave motion or a frozen frame.
 * Before any stop this turn, idle ambience is allowed ([stopSeq] == 0).
 */
fun labEffectActive(gestureId: String?, stopSeq: Long): Boolean = gestureId != null || stopSeq == 0L

/**
 * Cue state a camera renderer closure reads on every invocation. The camera module
 * re-invokes the renderer per recomposition; this slot is how 停/replay reaches it —
 * the closure must not hardcode active/tick (final review Important #2).
 */
data class LabCueSlot(val active: Boolean, val tick: Long, val cueId: String?)

/**
 * Unavailable camera engine: draw nothing. Passing null would hand the camera module
 * its own Lottie sticker branch — another engine's pixels under an "不可用" label
 * (final review Important #1). The reason line already names the cause.
 */
val blankCameraSticker: CameraFxStickerRenderer = { _, _ -> }

fun cameraStickerFor(engine: FxEngineId, gate: GateOutcome, slot: LabCueSlot): CameraFxStickerRenderer? {
    if (engine != FxEngineId.LOTTIE && gate != GateOutcome.Enabled) return blankCameraSticker
    return cameraRendererFor(engine, slot)
}

fun cameraRendererFor(engine: FxEngineId, slot: LabCueSlot): CameraFxStickerRenderer? = when (engine) {
    // null = the camera module keeps its own Lottie sticker branch (selected Lottie only).
    FxEngineId.LOTTIE -> null
    FxEngineId.RIVE -> { placement: StickerPlacementPx, modifier: Modifier ->
        if (slot.active) {
            key(slot.tick) {
                RiveStickerRenderer(riveRawResFor("fxlab_loopy"), placement, modifier)
            }
        }
    }
    FxEngineId.GL_PARTICLE -> { _, modifier: Modifier ->
        if (slot.active) {
            GLParticleOverlay(particleKindFor(slot.cueId), slot.tick, modifier)
        }
    }
    FxEngineId.THREE_WEBGL -> { _, modifier: Modifier ->
        ThreeOverlay(
            active = slot.active,
            command = slot.cueId?.let {
                "{\"type\":\"cue\",\"name\":\"$it\",\"seq\":${slot.tick}}"
            },
            modifier = modifier,
        )
    }
    FxEngineId.AGSL_SHADER -> { _, modifier: Modifier ->
        AgslGlowOverlay(active = slot.active, modifier = modifier)
    }
}

/** One place maps (engine, gate, scene) to a renderer; both scene families call this. */
@Composable
fun LabEffectLayer(state: LabUiState, modifier: Modifier = Modifier) {
    val active = labEffectActive(state.gestureId, state.stopSeq)
    when (rendererKindIfEnabled(state.engine, state.gate)) {
        null -> Text(
            labStatusLine(FxEngineCatalog.meta.getValue(state.engine), state.gate, state.scene),
            modifier,
        )
        RendererKind.LOTTIE_LAYER -> LottieFxLayer(
            gestureId = state.gestureId,
            gestureSeq = state.gestureSeq,
            reducedMotion = false,
            particles = true,
            stageZoomed = false,
            modifier = modifier,
        )
        // 停之后四个自研后端全部不渲染：待机循环（ambient GL / 呼吸 AGSL / rAF Three）
        // 或重键重播（Rive autoplay）都会把「已停」画成还在动（终审 Important #2）。
        RendererKind.RIVE -> if (active) {
            key(replayTick(state.gestureSeq, state.stopSeq)) {
                RiveStageRenderer(
                    riveRawResFor(if (state.gestureId == "wave") "fxlab_circle_move" else "fxlab_loopy"),
                    modifier,
                )
            }
        }
        RendererKind.GL -> if (active) {
            GLParticleOverlay(
                kind = particleKindFor(state.gestureId),
                tick = replayTick(state.gestureSeq, state.stopSeq),
                modifier = modifier,
            )
        }
        RendererKind.AGSL -> AgslGlowOverlay(
            active = active && state.gestureId != null,
            modifier = modifier,
        )
        RendererKind.THREE -> ThreeOverlay(
            active = active,
            command = cueCommand(state),
            modifier = modifier,
        )
    }
}

private fun particleKindFor(gestureId: String?): ParticleKind = when (gestureId) {
    "pat_head" -> ParticleKind.PAT_HEAD_BURST
    "wave" -> ParticleKind.WAVE_ARC
    else -> ParticleKind.AMBIENT_LOOP
}

/** seq is inside the payload so re-firing the same cue is a new command, not a dedup. */
private fun cueCommand(state: LabUiState): String? = state.gestureId?.let {
    "{\"type\":\"cue\",\"name\":\"$it\",\"seq\":${state.gestureSeq}}"
}
