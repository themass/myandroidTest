package com.openapi.ks.myapp.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 分层形象：按 manifest 的 z 顺序叠加 PNG，由 [LayeredAvatarEngine] 驱动呼吸、眨眼、嘴型、头发与待机。
 *
 * 触摸是这块的主要交互：点头、脸、胸、手各有即时的肢体反应，并在点中处出一圈涟漪和部位名；
 * 部位同时通过 [onTapped] 上报给 agent，由她决定要不要回应、怎么回应。
 *
 * 姿态只在绘制阶段读取：每帧只重画这一块，不重组页面。
 *
 * @param mouthLevel 她此刻的说话振幅 0..1（每帧读取）。返回 0 时闭嘴。
 */
@Composable
fun LayeredAvatar(
    pack: LayeredAvatarPack,
    modifier: Modifier = Modifier,
    mouthLevel: () -> Float = { 0f },
    onTapped: ((String) -> Unit)? = null,
) {
    val engine = remember(pack) { LayeredAvatarEngine() }
    var pose by remember(pack) { mutableStateOf(engine.update(0f)) }
    var touched by remember(pack) { mutableStateOf(false) }
    val currentMouth by rememberUpdatedState(mouthLevel)
    val currentOnTapped by rememberUpdatedState(onTapped)

    // 任意图层解不出来就整幅不画：缺图层的她（少只手、少张脸）比不画更糟。
    // 正常情况下加载侧已经拦掉了这种包，这里只是最后一道；中央声场仍在，页面不至于空。
    val bitmaps by produceState<Map<String, ImageBitmap>?>(initialValue = null, pack) {
        value = withContext(Dispatchers.IO) {
            pack.layers
                .mapNotNull { layer -> decodeScaled(layer.file)?.let { layer.id to it } }
                .toMap()
                .takeIf { it.size == pack.layers.size }
        }
    }

    LaunchedEffect(pack) {
        var last = -1L
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (last < 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                engine.mouthLevel = currentMouth()
                pose = engine.update(dt)
            }
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val ratio = pack.width.toFloat() / pack.height
        val width: Dp = min(maxWidth.value, maxHeight.value * ratio).dp
        val height: Dp = width / ratio
        Box(Modifier.size(width, height)) {
            Canvas(
                modifier = Modifier
                    .size(width, height)
                    .semantics { contentDescription = "她的形象，轻触头、脸、手可以互动" }
                    .pointerInput(pack) {
                        detectTapGestures { offset ->
                            val region = engine.tap(offset.x / size.width, offset.y / size.height)
                            if (region != null) {
                                touched = true
                                currentOnTapped?.invoke(region)
                            }
                        }
                    },
            ) {
                val images = bitmaps ?: return@Canvas
                drawPack(pack, images, pose)
                drawTapRipple(pose)
            }

            // 点中的部位名：在点中处上方浮一下，告诉用户「她感觉到了哪里」。
            val region = pose.tapRegion
            if (region != null) {
                val p = pose.tapProgress
                Text(
                    text = REGION_LABEL[region] ?: region,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .offset(
                            x = width * pose.tapX - 22.dp,
                            y = height * pose.tapY - 34.dp - 10.dp * p,
                        )
                        .alpha((1f - p).coerceIn(0f, 1f))
                        .background(Color(0x99231C45), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            // 首次提示：没点过之前告诉用户可以点她；点过一次就不再出现。
            if (!touched && bitmaps != null) {
                Text(
                    text = "轻触她的头、脸或手",
                    color = Color(0xCCF3F5FF),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color(0x66161F3D), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private val REGION_LABEL = mapOf(
    "head" to "摸摸头",
    "face" to "脸",
    "chest" to "胸口",
    "hand_l" to "左手",
    "hand_r" to "右手",
)

/**
 * 按 z 顺序画出所有层。变换的嵌套与原型一致：根节点的待机摆动包住全部；躯干与胸各自带呼吸；
 * 后发与刘海各自绕指定的原点摆动；双手可被点击抬起；脸红叠在脸上、刘海之下。
 */
private fun DrawScope.drawPack(pack: LayeredAvatarPack, images: Map<String, ImageBitmap>, pose: LayeredPose) {
    val k = size.width / LayeredPose.REF_WIDTH
    val pivot = Offset(size.width * pack.pivotX, size.height * pack.pivotY)
    val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())

    fun DrawScope.image(id: String) {
        images[id]?.let { drawImage(it, dstSize = dst) }
    }

    withTransform({
        translate(pose.rootX * k, pose.rootY * k)
        rotate(pose.rootRot, pivot)
    }) {
        withTransform({
            rotate(pose.hairSideRot, Offset(size.width * 0.5f, size.height * 0.22f))
        }) {
            image("hair_back")
        }
        withTransform({
            translate(0f, pose.torsoY * k)
            scale(1f, pose.torsoSy, pivot)
        }) {
            image("body")
            withTransform({
                translate(0f, pose.chestY * k)
                scale(1f, pose.chestSy, Offset(size.width * 0.5f, size.height * 0.52f))
            }) {
                image("chest")
            }
        }
        image("face")
        pack.variant("eye_l", pose.eyeL)?.let { image(it.id) }
        pack.variant("eye_r", pose.eyeR)?.let { image(it.id) }
        pack.variant("mouth", pose.mouth)?.let { image(it.id) }
        if (pose.blush > 0.01f) drawBlush(pose.blush)
        withTransform({
            rotate(pose.bangsRot, Offset(size.width * 0.5f, size.height * 0.18f))
        }) {
            image("hair_front")
        }
        withTransform({ translate(0f, pose.handLY * k) }) { image("hand_l") }
        withTransform({ translate(0f, pose.handRY * k) }) { image("hand_r") }
    }
}

/** 两颊的红晕：位置按素材实测（眼睛下方 y≈0.345，两眼外侧 x≈0.43 / 0.57）。 */
private fun DrawScope.drawBlush(amount: Float) {
    val r = size.width * 0.04f
    val color = Color(0xFFFF7A9A).copy(alpha = 0.45f * amount)
    listOf(0.43f, 0.57f).forEach { x ->
        drawOval(
            color = color,
            topLeft = Offset(size.width * x - r, size.height * 0.345f - r * 0.55f),
            size = androidx.compose.ui.geometry.Size(r * 2f, r * 1.1f),
        )
    }
}

/** 点中处的一圈涟漪：从手指位置扩散并淡出。 */
private fun DrawScope.drawTapRipple(pose: LayeredPose) {
    if (pose.tapRegion == null) return
    val p = pose.tapProgress.coerceIn(0f, 1f)
    val center = Offset(size.width * pose.tapX, size.height * pose.tapY)
    drawCircle(
        color = Color(0xFFB9AEFF).copy(alpha = 0.55f * (1f - p)),
        radius = size.width * (0.03f + 0.10f * p),
        center = center,
        style = Stroke(width = 2.dp.toPx()),
    )
    drawCircle(
        color = Color(0xFFB9AEFF).copy(alpha = 0.18f * (1f - p)),
        radius = size.width * (0.02f + 0.05f * p),
        center = center,
    )
}
