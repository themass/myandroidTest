package com.openapi.app.fxlab

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import com.openapi.app.R
import kotlinx.coroutines.isActive

/** AGSL RuntimeShader needs API 33; below that the gate reports it, never renders blank. */
fun agslSupported(sdkInt: Int): Boolean = sdkInt >= 33

fun agslUniforms(tSeconds: Float, intensity: Float): Map<String, Float> = mapOf(
    "t" to (tSeconds.takeIf { it.isFinite() } ?: 0f).coerceAtLeast(0f),
    // Clamped here so a bad call site cannot blow the halo out into a white sheet.
    "intensity" to (intensity.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f),
    "centerX" to 0.5f,
    "centerY" to 0.45f,
)

/**
 * Halo overlay drawn with a ShaderBrush. Construction is wrapped because GPU drivers differ:
 * a parse failure must surface in the Lab, per the design's no-silent-blank rule.
 */
@Composable
fun AgslGlowOverlay(active: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    var failure by remember { mutableStateOf<String?>(null) }
    val source = remember {
        runCatching { context.resources.openRawResource(R.raw.fxlab_agsl).reader().readText() }
            .onFailure { failure = it.message ?: it.javaClass.simpleName }
            .getOrNull()
    }
    var tSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active || source == null) return@LaunchedEffect
        val start = System.nanoTime()
        while (isActive) {
            tSeconds = (System.nanoTime() - start) / 1_000_000_000f
            delay(16)
        }
    }
    when {
        failure != null -> Text("AGSL 不可用：$failure", modifier, color = Color.Red)
        !agslSupported(Build.VERSION.SDK_INT) ->
            Text("AGSL 需要 API 33+（本机 ${Build.VERSION.SDK_INT}）", modifier, color = Color.Yellow)
        source != null -> Canvas(modifier) {
            val shader = runCatching {
                RuntimeShader(source).apply {
                    agslUniforms(tSeconds, 1f).forEach { (name, value) -> setFloatUniform(name, value) }
                }
            }.onFailure { failure = it.message ?: it.javaClass.simpleName }.getOrNull()
            if (shader != null) drawRect(ShaderBrush(shader))
        }
    }
}

private suspend fun delay(ms: Long) = kotlinx.coroutines.delay(ms)
