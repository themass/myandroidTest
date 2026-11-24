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

fun cameraRendererFor(engine: FxEngineId): CameraFxStickerRenderer? = when (engine) {
    // null = the camera module keeps its own Lottie sticker branch.
    FxEngineId.LOTTIE -> null
    FxEngineId.RIVE -> { placement: StickerPlacementPx, modifier: Modifier ->
        RiveStickerRenderer(riveRawResFor("fxlab_loopy"), placement, modifier)
    }
    FxEngineId.GL_PARTICLE -> { _, modifier: Modifier ->
        GLParticleStickerRenderer(StickerPlacementPx(0f, 0f, 0f, 0f), modifier)
    }
    FxEngineId.THREE_WEBGL -> { _, modifier: Modifier ->
        ThreeOverlay(active = true, command = null, modifier = modifier)
    }
    FxEngineId.AGSL_SHADER -> { _, modifier: Modifier ->
        AgslGlowOverlay(active = true, modifier = modifier)
    }
}

/** One place maps (engine, gate, scene) to a renderer; both scene families call this. */
@Composable
fun LabEffectLayer(state: LabUiState, modifier: Modifier = Modifier) {
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
        RendererKind.RIVE -> key(replayTick(state.gestureSeq, state.stopSeq)) {
            RiveStageRenderer(
                riveRawResFor(if (state.gestureId == "wave") "fxlab_circle_move" else "fxlab_loopy"),
                modifier,
            )
        }
        RendererKind.GL -> GLParticleOverlay(
            kind = particleKindFor(state.gestureId),
            tick = replayTick(state.gestureSeq, state.stopSeq),
            modifier = modifier,
        )
        RendererKind.AGSL -> AgslGlowOverlay(active = state.gestureId != null, modifier = modifier)
        RendererKind.THREE -> ThreeOverlay(
            active = true,
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
