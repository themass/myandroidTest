package com.openapi.ks.myapp.ui.sound.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Observable UI state for the music detail screen.
 *
 * Written by SoundDetailActivity (service connection and PlayService broadcasts); read by
 * [SoundDetailScreen]. Defaults mirror the legacy XML views before any broadcast arrives.
 */
class SoundDetailUiState {

    /** Track name shown in the big title (empty until the service reports a track). */
    var trackTitle: String by mutableStateOf("")

    /** True when the transport button shows the pause icon. */
    var playing: Boolean by mutableStateOf(false)

    /** True while MUSIC_PREPAREING: spinner replaces the elapsed-time label. */
    var preparing: Boolean by mutableStateOf(false)

    var timeText: String by mutableStateOf("")

    var durationText: String by mutableStateOf("")

    /** SeekBar max; SeekBar default is 100 until MUSIC_DURATION arrives. */
    var progressMax: Int by mutableStateOf(DEFAULT_PROGRESS_MAX)

    /** SeekBar progress in milliseconds. */
    var progress: Int by mutableStateOf(0)

    companion object {
        const val DEFAULT_PROGRESS_MAX = 100
    }
}
