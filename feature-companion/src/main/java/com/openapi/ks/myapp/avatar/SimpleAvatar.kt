package com.openapi.ks.myapp.avatar

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * 把 [SimpleAvatarRenderer] 的状态画出来。
 *
 * 渲染器只管状态，插值都在这里：呼吸是一个无限动画，眨眼是换一张图。所有素材
 * 缺失都逐级回退——表情缺用立绘，立绘缺用占位块，眼睛/嘴缺就少那一层，界面
 * 结构始终可用。
 */
@Composable
fun SimpleAvatar(
    renderer: SimpleAvatarRenderer,
    modifier: Modifier = Modifier,
) {
    val spec by renderer.spec.collectAsState()
    val avatarState by renderer.state.collectAsState()
    val expression by renderer.expressionRes.collectAsState()
    val mouthOpen by renderer.mouthOpen.collectAsState()
    val eyeOpen by renderer.eyeOpen.collectAsState()
    val expressionFile by renderer.expressionFile.collectAsState()

    // 呼吸：说话时略快、幅度略大。幅度只有 1% 上下——目的是让静态立绘不显得死板，
    // 太大就会变成抖动。时长变化时 rememberInfiniteTransition 会重新起动画，
    // 副作用是切状态那一刻呼吸相位跳一下，视觉上察觉不到，不值得为它做平滑。
    val speaking = avatarState == AvatarState.SPEAKING
    val transition = rememberInfiniteTransition(label = "avatarBreath")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (speaking) 1.012f else 1.006f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (speaking) 1_800 else 3_200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "avatarBreathScale",
    )

    Box(
        // 呼吸缩放整体作用于所有图层，否则表情和嘴会从立绘上漂开。
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.Center,
    ) {
        val loaded = spec
        val files = loaded?.files
        if (files != null) {
            // 下载的形象包：按层叠加，缺的层直接不画。顺序：下半身 → 立绘 → 胸腔 → 表情 → 眼睛 → 嘴 → 点缀。
            FileLayer(files.lowerBody)
            FileLayer(files.portrait)
            FileLayer(files.chest)
            val face = expressionFile
            if (face != null && face != files.portrait) FileLayer(face)
            FileLayer(if (eyeOpen) files.eyeOpen else files.eyeClosed ?: files.eyeOpen)
            FileLayer(if (mouthOpen) files.mouthOpen else files.mouthClosed)
            FileLayer(files.blush)
            FileLayer(files.highlight)
        } else if (loaded?.portraitRes == null) {
            // 素材还没做完（或还没 bind）：给一个中性占位，页面布局仍然成立。
            Box(
                Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            AvatarLayer(loaded.portraitRes)

            // 表情和立绘是两张独立素材；同 id 时再画一遍纯属浪费。
            val face = expression
            if (face != null && face != loaded.portraitRes) AvatarLayer(face)

            // 眨眼：只有配了眼睛素材才有这一层。闭眼图缺失时退到睁眼图，
            // 否则眨眼那一瞬整层眼睛会消失。
            AvatarLayer(
                if (eyeOpen) loaded.eyeOpenRes else loaded.eyeClosedRes ?: loaded.eyeOpenRes,
            )

            AvatarLayer(if (mouthOpen) loaded.mouthOpenRes else loaded.mouthClosedRes)
        }
    }
}

/**
 * 单层素材，按整幅尺寸绘制（美术按同一画布出图），叠加顺序由调用处决定——换
 * 角色只要换图，不用改代码。`null` 表示没有这一层，直接不画。
 */
@Composable
private fun AvatarLayer(@DrawableRes res: Int?) {
    if (res != null) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * 单层素材（下载形象）。解码放在 IO 线程，并按 [MAX_LAYER_PX] 缩小：形象包的图可能比屏幕大得多，
 * 整幅解码几层会占掉不少内存。以文件路径和修改时间为键，形象包更新后会重新解码。
 */
@Composable
private fun FileLayer(file: File?) {
    if (file == null) return
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) { decodeScaled(file) }
    }
    bitmap?.let {
        Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

private const val MAX_LAYER_PX = 1600

private fun decodeScaled(file: File): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > MAX_LAYER_PX || bounds.outHeight / sample > MAX_LAYER_PX) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}
