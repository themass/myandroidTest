package com.openapi.ks.myapp.ui.channel

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import com.openapi.core.view.view.MyWebView
import com.openapi.core.R

/**
 * Observable page-loading flag shared between the WebView client callbacks (Java)
 * and the Compose page. Initially shown, matching the legacy ProgressBar default.
 */
class WebviewProgress {
    var shown: Boolean by mutableStateOf(true)
}

/**
 * Creates the Compose host for a WebView screen. Replaces the legacy XML layout:
 * an optional full-bleed background drawable, padding margin_3, the WebView, an optional
 * favorite overlay aligned bottom-end, and a centered progress indicator on top.
 */
fun createWebviewComposeView(
    context: Context,
    progress: WebviewProgress,
    createWebView: (Context) -> MyWebView,
    createFavorite: ((Context) -> View)?,
    @DrawableRes backgroundRes: Int?
): ComposeView {
    return ComposeView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            WebviewPage(
                progress = progress,
                createWebView = createWebView,
                createFavorite = createFavorite,
                backgroundRes = backgroundRes
            )
        }
    }
}

@Composable
private fun WebviewPage(
    progress: WebviewProgress,
    createWebView: (Context) -> MyWebView,
    createFavorite: ((Context) -> View)?,
    @DrawableRes backgroundRes: Int?
) {
    val outerModifier = if (backgroundRes != null) {
        Modifier
            .fillMaxSize()
            .paint(painterResource(backgroundRes), contentScale = ContentScale.FillBounds)
    } else {
        Modifier.fillMaxSize()
    }
    Box(
        modifier = outerModifier.padding(dimensionResource(R.dimen.margin_3))
    ) {
        AndroidView(
            factory = createWebView,
            modifier = Modifier.fillMaxSize()
        )
        if (createFavorite != null) {
            AndroidView(
                factory = createFavorite,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = dimensionResource(R.dimen.margin_22),
                        bottom = dimensionResource(R.dimen.margin_55)
                    )
            )
        }
        if (progress.shown) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
