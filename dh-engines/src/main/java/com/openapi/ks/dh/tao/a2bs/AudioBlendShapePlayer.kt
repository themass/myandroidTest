package com.openapi.ks.dh.tao.a2bs

/**
 * No-op stub of the upstream A2BS player.
 *
 * Task-6 acceptance is idle NNR on screen (mouth/A2BS not required). Upstream
 * [NnrAvatarRender.doFrame] reads [isPlaying]/[currentTime]/[totalTime]/[isBuffering]
 * and [update] — all default to idle-safe values here so the mesh can render without
 * dragging in TTS / A2BS / AudioChunks / MainActivity.
 */
class AudioBlendShapePlayer {

    class PlayingStatus {
        var isBuffering: Boolean = false
        var smoothToIdlePercent: Float = -1f
        var smoothToTalkPercent: Float = -1f
    }

    val currentTime: Long get() = 0L
    val totalTime: Long get() = 0L
    val isPlaying: Boolean get() = false
    val isBuffering: Boolean get() = false

    fun update(): PlayingStatus = PlayingStatus()
}
