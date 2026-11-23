package com.openapi.feature.content.common

import android.graphics.Color as AndroidColor
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.openapi.common.util.lang.StringUtils
import com.openapi.feature.content.R
import com.openapi.core.model.RecommendVo
import com.openapi.core.config.Constants
import com.openapi.core.image.ImagePhotoLoad
import kotlinx.coroutines.flow.distinctUntilChanged
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** Glide load used by NormalShowTypeHandle / BlurShowTypeHandle / TextBelowShowTypeHandle. */
internal fun loadPhoto(iv: ImageView, url: String, onReady: () -> Unit) {
    if (url.endsWith(".txt")) {
        fetchBase64(url) { base64 ->
            iv.post {
                if (iv.tag == url) {
                    Glide.with(iv)
                        .load(base64)
                        .apply(ImagePhotoLoad.options)
                        .transition(DrawableTransitionOptions.withCrossFade(400))
                        .into(iv)
                }
            }
        }
        return
    }
    Glide.with(iv)
        .load(url)
        .apply(ImagePhotoLoad.options)
        .transition(DrawableTransitionOptions.withCrossFade(500))
        .listener(object : RequestListener<Drawable> {
            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean = false

            override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                onReady()
                return false
            }
        })
        .into(iv)
}

/** Legacy ShowTypeHandle.fetchBase64ImageFromUrl. */
internal val base64Client by lazy { OkHttpClient() }

internal fun fetchBase64(url: String, onBody: (String) -> Unit) {
    val request = Request.Builder().url(url).build()
    base64Client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            com.openapi.common.util.log.LogUtil.e("onFailure", e)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { r ->
                val body = r.body
                if (r.isSuccessful && body != null) {
                    onBody(body.string())
                }
            }
        }
    })
}

internal fun parseColorOrTransparent(value: String?): Int {
    return try {
        AndroidColor.parseColor(value)
    } catch (e: Exception) {
        AndroidColor.TRANSPARENT
    }
}

@Composable
internal fun dimenToSp(id: Int): TextUnit {
    val density = LocalDensity.current
    val px = LocalContext.current.resources.getDimension(id)
    return with(density) { px.toSp() }
}
