package com.openapi.app.fxlab

import android.view.View
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop
import com.openapi.app.R
import com.openapi.feature.camerafx.StickerPlacementPx

@Composable
fun RiveStickerRenderer(resId: Int, placement: StickerPlacementPx, modifier: Modifier) {
    RiveStageRenderer(resId, modifier)
}

/**
 * Rive 的 androidx.startup 自动初始化在本 app 被 tools:node="remove" 摘掉（见 APK 合并
 * manifest），所以 native 注册不会自动发生。构造任何 SDK 对象前必须 ensure 一次，
 * 否则 JNI 报 "No implementation found for FileAssetLoader.constructor()"（真机踩过）。
 */
class RiveRuntimeGate {
    @Volatile private var done = false
    @Volatile var failure: String? = null
        private set

    fun ensure(init: () -> Unit) {
        if (done) return
        synchronized(this) {
            if (done) return
            runCatching(init).onFailure { failure = it.message ?: it.javaClass.simpleName }
            done = true // 失败也只试一次，失败原因由调用方展示
        }
    }
}

private val riveGate = RiveRuntimeGate()

/** The official fixtures contain linear animations, supported by this SDK's view API. */
@Composable
fun RiveStageRenderer(resId: Int, modifier: Modifier) {
    var failure by remember(resId) { mutableStateOf<String?>(null) }
    if (failure != null) {
        Text("Rive 加载失败：$failure", modifier.border(1.dp, Color.Red), color = Color.Red)
    } else {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                // runCatching: the SDK loads a native library at construction. If that ever
                // fails (or a fixture is corrupt), the Lab shows the reason instead of a blank.
                runCatching {
                    riveGate.ensure { app.rive.runtime.kotlin.core.Rive.init(context) }
                    riveGate.failure?.let { error(it) }
                    RiveAnimationView(context).apply {
                        isOpaque = false
                        // Decorative overlay: never claim avatar/camera touch events.
                        setOnTouchListener { _, _ -> false }
                        setRiveResource(resId, autoplay = true, fit = Fit.CONTAIN, loop = Loop.LOOP)
                    }
                }.onFailure { failure = it.message ?: it.javaClass.simpleName }
                    .getOrNull() ?: View(context)
            },
            onRelease = { (it as? RiveAnimationView)?.stop() },
        )
    }
}

fun riveRawResFor(engineAsset: String): Int = when (engineAsset) {
    "fxlab_loopy" -> R.raw.fxlab_loopy
    "fxlab_circle_move" -> R.raw.fxlab_circle_move
    else -> 0
}
