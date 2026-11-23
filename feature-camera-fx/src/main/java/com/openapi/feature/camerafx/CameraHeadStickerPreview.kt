package com.openapi.feature.camerafx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlin.math.roundToInt

/**
 * Android Studio 预览：无相机，用固定归一化锚点验证 fx_pat_head 叠层几何。
 * 与 [CameraFxScreen] 贴纸分支共用 [CanvasMap] + [stickerAtHeadTop]。
 */
@Preview(name = "Head sticker overlay", showBackground = true, widthDp = 400, heightDp = 640)
@Composable
fun CameraHeadStickerOverlayPreview() {
    val map = CanvasMap(400f, 640f, 720, 960, fill = true, mirrorX = true)
    val head = Point2d(0.5f, 0.22f)
    val place = stickerAtHeadTop(map, head, faceWidthNorm = 0.32f, tiltDegForDisplay = 6f)
    val half = place.sizePx / 2f
    val density = LocalDensity.current
    val stickerSize = with(density) { place.sizePx.toDp() }
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("fx/fx_pat_head.json"))

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),
    ) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                iterations = Int.MAX_VALUE,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (place.centerX - half).roundToInt(),
                            (place.centerY - half).roundToInt(),
                        )
                    }
                    .size(stickerSize)
                    .rotate(place.rotationDeg),
            )
        }
    }
}
