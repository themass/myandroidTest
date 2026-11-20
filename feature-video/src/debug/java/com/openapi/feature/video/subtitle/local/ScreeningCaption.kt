package com.openapi.feature.video.subtitle.local

import java.io.Reader

internal class ScreeningCaption(reader: Reader) {
    private val track = LocalSubtitleTrack.read(reader)
    fun at(position: Long, delay: Int): String = track.at(position, delay)
}
