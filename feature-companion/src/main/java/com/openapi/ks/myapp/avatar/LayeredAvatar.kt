package com.openapi.ks.myapp.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 分层形象：按 manifest 的 z 顺序叠加 PNG，由 [LayeredAvatarEngine] 驱动呼吸、眨眼、嘴型、头发与待机。
 *
 * 绘制在一块 [Canvas] 里完成，姿态只在绘制阶段读取：每帧只重画这一块，不重组页面。
 * 点击按热区换算到画布比例后交给引擎；被点中的部位由引擎给出反馈，并通过 [onTapped] 上报。
 *
 * [mouthLevel] 为 null 时嘴保持闭合。
 */
@Composable
fun LayeredAvatar(
    pack: LayeredAvatarPack,
    modifier: Modifier = Modifier,
    mouthLevel: StateFlow<Float>? = null,
    onTapped: ((String) -> Unit)? = null,
) {
    val engine = remember(pack) { LayeredAvatarEngine() }
    var pose by remember(pack) { mutableStateOf(engine.update(0f)) }

    val bitmaps by produceState<Map<String, ImageBitmap>?>(initialValue = null, pack) {
        value = withContext(Dispatchers.IO) {
            pack.layers.mapNotNull { layer -> decodeScaled(layer.file)?.let { layer.id to it } }.toMap()
        }
    }

    if (mouthLevel != null) {
        val level by mouthLevel.collectAsState()
        LaunchedEffect(level) { engine.mouthLevel = level }
    }

    LaunchedEffect(pack) {
        var last = -1L
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (last < 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                pose = engine.update(dt)
            }
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val ratio = pack.width.toFloat() / pack.height
        val width: Dp = min(maxWidth.value, maxHeight.value * ratio).dp
        val height: Dp = width / ratio
        Canvas(
            modifier = Modifier
                .size(width, height)
                .pointerInput(pack, onTapped) {
                    detectTapGestures { offset ->
                        val region = engine.tap(offset.x / size.width, offset.y / size.height)
                        if (region != null) {
                            pose = engine.update(0f)
                            onTapped?.invoke(region)
                        }
                    }
                },
        ) {
            val images = bitmaps ?: return@Canvas
            drawPack(pack, images, pose)
        }
    }
}

/**
 * 按 z 顺序画出所有层。变换的嵌套与原型一致：根节点的待机摆动包住全部；躯干与胸各自带呼吸；
 * 后发与刘海各自绕指定的原点摆动；脸、眼、嘴与手不做额外变换。
 */
private fun DrawScope.drawPack(pack: LayeredAvatarPack, images: Map<String, ImageBitmap>, pose: LayeredPose) {
    val k = size.width / LayeredPose.REF_WIDTH
    val pivot = Offset(size.width * pack.pivotX, size.height * pack.pivotY)
    val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())

    fun DrawScope.image(id: String) {
        images[id]?.let { drawImage(it, dstSize = dst) }
    }

    fun DrawScope.eye(group: String, shape: String) {
        pack.variant(group, shape)?.let { image(it.id) }
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
        eye("eye_l", pose.eyeL)
        eye("eye_r", pose.eyeR)
        pack.variant("mouth", pose.mouth)?.let { image(it.id) }
        withTransform({
            rotate(pose.bangsRot, Offset(size.width * 0.5f, size.height * 0.18f))
        }) {
            image("hair_front")
        }
        image("hand_l")
        image("hand_r")
    }
}
