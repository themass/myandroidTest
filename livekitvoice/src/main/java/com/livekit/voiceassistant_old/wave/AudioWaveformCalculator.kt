package com.livekit.voiceassistant.wave
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.livekit.voiceassistant_old.wave.AudioSizeChangeListener
import io.livekit.android.annotations.Beta
import io.livekit.android.compose.types.TrackReference
import io.livekit.android.compose.ui.audio.AudioTrackSinkFlow
import io.livekit.android.compose.ui.audio.FFTAudioAnalyzer
import io.livekit.android.room.track.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * 用于计算音频波纹但不展示界面，当音频发生变化时回调接口
 */
@Beta
@Composable
fun AudioWaveformCalculator(
    audioTrackRef: TrackReference?,
    barCount: Int = 1,
    loPass: Int = 50,
    hiPass: Int = 150,
    listener: AudioSizeChangeListener
) {

    val audioSink = remember(audioTrackRef) { AudioTrackSinkFlow() }
    val audioProcessor = remember(audioTrackRef) { FFTAudioAnalyzer() }
    val fftFlow = audioProcessor.fftFlow
    var amplitudes by remember(audioTrackRef, barCount) { mutableStateOf(FloatArray(barCount)) }

    // Attach the sink to the track.
    DisposableEffect(key1 = audioTrackRef) {
        val track = audioTrackRef?.publication?.track as? AudioTrack
        track?.addSink(audioSink)

        onDispose {
            track?.removeSink(audioSink)
            audioProcessor.release()
        }
    }

    // Configure audio processor as needed.
    LaunchedEffect(key1 = audioTrackRef) {
        audioSink.audioFormat.collect {
            audioProcessor.configure(it)
        }
    }

    // Collect audio bytes and pass to audio fft analyzer
    LaunchedEffect(key1 = audioTrackRef) {
        launch(Dispatchers.IO) {
            audioSink.audioFlow.collect { (buffer, _) ->
                audioProcessor.queueInput(buffer)
            }
        }
    }

    // Process audio bytes into desired bars
    LaunchedEffect(audioTrackRef, barCount) {
        var participantInfo = audioTrackRef?.participant?.participantInfo
        Log.i("mytag","participantInfo：$participantInfo")
        launch(Dispatchers.IO) {
            fftFlow.collect { fft ->
                val amplitude = calculateSingleAmplitude(fft.toList())
                if(amplitude>1) {
                    listener.onAudioSizeChanged(amplitude*85*2)
                }
            }
        }
    }
}
private const val MIN_CONST = 2f
private const val MAX_CONST = 25f
private fun calculateSingleAmplitude(fft: List<Float>): Float {
//    var accum = 0f
//    for (i in 0 until fft.size step 2) {
//        // Convert real and imaginary part to get energy
//        val realSq = fft[i].toDouble().pow(2.0)
//        val imaginarySq = fft[i + 1].toDouble().pow(2.0)
//        val raw = sqrt(realSq + imaginarySq).toFloat()
//        accum += raw
//    }
//    val average = if (fft.isNotEmpty()) accum / (fft.size / 2) else 0f
//
//    // 假设我们可以预先计算出可能的最大值，这里简单假设一个初始值
//    // 实际应用中可能需要通过多次测试找到合适的最大值
//    val maxPossibleValue = 100f // 这个值需要根据实际情况调整
//
//    // 线性映射到 0 - 8500 范围
//    return ((average / maxPossibleValue) * 8500).toFloat().coerceIn(0F, 8500F)
    return fft.max();
}

private fun calculateAmplitudeBarsFromFFT(
    fft: List<Float>,
    averages: FloatArray,
    barCount: Int,
): FloatArray {
    val amplitudes = FloatArray(barCount)
    if (fft.isEmpty()) {
        return amplitudes
    }

    // We average out the values over 3 occurences (plus the current one), so big jumps are smoothed out
    // Iterate over the entire FFT result array.
    for (barIndex in 0 until barCount) {
        // Note: each FFT is a real and imaginary pair.
        // Scale down by 2 and scale back up to ensure we get an even number.
        val prevLimit = (round(fft.size.toFloat() / 2 * barIndex / barCount).toInt() * 2)
            .coerceIn(0, fft.size - 1)
        val nextLimit = (round(fft.size.toFloat() / 2 * (barIndex + 1) / barCount).toInt() * 2)
            .coerceIn(0, fft.size - 1)

        var accum = 0f
        // Here we iterate within this single band
        for (i in prevLimit until nextLimit step 2) {
            // Convert real and imaginary part to get energy

            val realSq = fft[i]
                .toDouble()
                .pow(2.0)
            val imaginarySq = fft[i + 1]
                .toDouble()
                .pow(2.0)
            val raw = sqrt(realSq + imaginarySq).toFloat()

            accum += raw
        }

        // A window might be empty which would result in a 0 division
        if ((nextLimit - prevLimit) != 0) {
            accum /= (nextLimit - prevLimit)
        } else {
            accum = 0.0f
        }

        val smoothingFactor = 5
        var avg = averages[barIndex]
        avg += (accum - avg / smoothingFactor)
        averages[barIndex] = avg

        var amplitude = avg.coerceIn(MIN_CONST, MAX_CONST)
        amplitude -= MIN_CONST
        amplitude /= (MAX_CONST - MIN_CONST)
        amplitudes[barIndex] = amplitude
    }

    return amplitudes
}
