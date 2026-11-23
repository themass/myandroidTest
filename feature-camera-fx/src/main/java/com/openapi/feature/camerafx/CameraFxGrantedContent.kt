package com.openapi.feature.camerafx

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * 已授权相机后的预览 + 可选 HUD + Lottie 头顶贴纸。
 * [drawWireframe] 关时仅贴纸（通话页小窗）；开时与全屏 lab 一致。
 */
@Composable
fun CameraFxGrantedContent(
    modifier: Modifier = Modifier,
    overlayPolicy: CameraFxOverlayPolicy = CameraFxOverlayPolicy(),
) {
    val showDebugHud = overlayPolicy.showDebugHud
    val drawWireframe = overlayPolicy.drawWireframe
    val drawStickers = overlayPolicy.drawStickers
    val stickerAssetPath = overlayPolicy.lottieAssetPath
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val frameFlow = remember { MutableStateFlow<FrameFx?>(null) }
    val fatalFlow = remember { MutableStateFlow<String?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        FxAnalyzer(
            context = context,
            onFrame = { frameFlow.value = it },
            onFatal = { fatalFlow.value = it },
        )
    }
    DisposableEffect(analyzer, analysisExecutor) {
        onDispose {
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var viewSize by remember { mutableStateOf(0 to 0) }
    var layoutW by remember { mutableStateOf(0) }
    var layoutH by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (provider == null) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        provider = future.get()
                    } catch (t: Throwable) {
                        fatalFlow.value = "相机服务启动失败：${t.javaClass.simpleName}"
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
    }

    LaunchedEffect(provider, previewView, lifecycleOwner, viewSize) {
        val p = provider ?: return@LaunchedEffect
        val view = previewView ?: return@LaunchedEffect
        if (viewSize.first == 0 || viewSize.second == 0) return@LaunchedEffect
        try {
            p.unbindAll()
            val selector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(720, 960),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build()
            val preview = Preview.Builder()
                .setResolutionSelector(selector)
                .build()
                .also { it.setSurfaceProvider(view.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setOutputImageRotationEnabled(true)
                .build()
                .also { it.setAnalyzer(analysisExecutor, analyzer) }
            val viewPort = view.viewPort ?: view.getViewPort(preview.targetRotation)
            val groupBuilder = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(analysis)
            if (viewPort != null) groupBuilder.setViewPort(viewPort)
            p.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                groupBuilder.build(),
            )
        } catch (t: Throwable) {
            fatalFlow.value = "相机启动失败：${t.javaClass.simpleName}"
        }
    }

    val frame by frameFlow.collectAsState()
    val fatal by fatalFlow.collectAsState()
    val stickerComposition by rememberLottieComposition(LottieCompositionSpec.Asset(stickerAssetPath))

    Box(
        modifier
            .background(Color.Black)
            .onSizeChanged { layoutW = it.width; layoutH = it.height },
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }.also { v ->
                    previewView = v
                    v.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        val next = v.width to v.height
                        if (next.first > 0 && next.second > 0 && next != viewSize) {
                            viewSize = next
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        val fx = frame
        if (fx != null && drawWireframe) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .zIndex(1f),
            ) {
                drawFxWireframe(fx)
            }
        }

        if (drawStickers) {
            val comp = stickerComposition
            val frameForSticker = frame
            val density = LocalDensity.current
            if (comp != null && frameForSticker != null && layoutW > 0 && layoutH > 0) {
                val map = CanvasMap(
                    canvasW = layoutW.toFloat(),
                    canvasH = layoutH.toFloat(),
                    imgW = frameForSticker.imgW,
                    imgH = frameForSticker.imgH,
                    fill = true,
                    mirrorX = true,
                )
                frameForSticker.faces.forEach { face ->
                    val head = face.headTop ?: return@forEach
                    val fw = face.bounds?.width ?: return@forEach
                    val place = stickerAtHeadTop(map, head, fw, face.tiltDeg)
                    val half = place.sizePx / 2f
                    val stickerSize = with(density) { place.sizePx.toDp() }
                    LottieAnimation(
                        composition = comp,
                        iterations = Int.MAX_VALUE,
                        modifier = Modifier
                            .zIndex(1f)
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

        if (showDebugHud) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 56.dp, end = 12.dp)
                    .zIndex(2f)
                    .background(Color(0x99000000))
                    .padding(8.dp),
            ) {
                val f = frame
                val faceN = f?.faces?.size ?: 0
                val poseN = f?.poses?.count { it.segments.isNotEmpty() } ?: 0
                val handN = f?.hands?.count { it.segments.isNotEmpty() } ?: 0
                val gestures = f?.hands
                    ?.map { it.gesture.hudLabel() }
                    ?.filter { it != "—" }
                    ?.joinToString(",") ?: ""
                val tilt = f?.faces?.firstOrNull()?.tiltDeg
                Text(
                    "fx ${f?.fps?.let { "%.1f fps".format(it) } ?: "--"} · 脸 $faceN · 姿态 $poseN · 手 $handN" +
                        (if (gestures.isNotEmpty()) " · 手势 $gestures" else "") +
                        (if (tilt != null) " · 倾角 %.1f°".format(tilt) else "") +
                        (f?.let { " · ${it.imgW}x${it.imgH}" } ?: ""),
                    color = Color.White,
                    fontSize = 13.sp,
                )
                Text("本机处理 · 不上传 · 不落盘", color = Color(0xFF9FFF9F), fontSize = 11.sp)
                fatal?.let {
                    Text(it, color = Color(0xFFFF8080), fontSize = 12.sp)
                }
                if (f != null && faceN == 0) {
                    Text(
                        "未识别人脸：正对镜头、光线充足；线框/贴纸需脸>0",
                        color = Color(0xFFFFCC80),
                        fontSize = 11.sp,
                    )
                }
                if (f != null && faceN > 0 && handN == 0) {
                    Text(
                        "手部：把手放进画面；手势每 2 帧更新",
                        color = Color(0xFFFFCC80),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/** 通话页右下角小窗：仅贴纸，无 HUD/线框。 */
@Composable
fun CameraFxStickerPip(modifier: Modifier = Modifier) {
    CameraFxGrantedContent(
        modifier = modifier,
        overlayPolicy = CameraFxOverlayPolicy(
            drawWireframe = false,
            drawStickers = true,
            showDebugHud = false,
        ),
    )
}

internal fun DrawScope.drawFxWireframe(fx: FrameFx) {
    val map = CanvasMap(
        canvasW = size.width,
        canvasH = size.height,
        imgW = fx.imgW,
        imgH = fx.imgH,
        fill = true,
        mirrorX = true,
    )
    for (pose in fx.poses) {
        for (seg in pose.segments) {
            val a = map.map(seg.a)
            val b = map.map(seg.b)
            drawLine(
                color = Color(0xFF00E5FF),
                start = Offset(a.x, a.y),
                end = Offset(b.x, b.y),
                strokeWidth = 4f,
            )
        }
    }
    for (hand in fx.hands) {
        for (seg in hand.segments) {
            val a = map.map(seg.a)
            val b = map.map(seg.b)
            drawLine(
                color = Color(0xFFFF9800),
                start = Offset(a.x, a.y),
                end = Offset(b.x, b.y),
                strokeWidth = 3f,
            )
        }
    }
    for (face in fx.faces) {
        for (line in face.polylines) {
            if (line.points.size < 2) continue
            val path = Path()
            val first = map.map(line.points.first())
            path.moveTo(first.x, first.y)
            for (p in line.points.drop(1)) {
                val o = map.map(p)
                path.lineTo(o.x, o.y)
            }
            if (line.closed) path.close()
            drawPath(path, color = Color(0xFFFFEB3B), style = Stroke(width = 2.5f))
        }
        face.bounds?.let { b ->
            val tl = map.map(Point2d(b.x, b.y))
            val br = map.map(Point2d(b.x + b.width, b.y + b.height))
            drawRect(
                color = Color.White,
                topLeft = Offset(minOf(tl.x, br.x), minOf(tl.y, br.y)),
                size = Size(kotlin.math.abs(br.x - tl.x), kotlin.math.abs(br.y - tl.y)),
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
            )
        }
        face.headTop?.let { h ->
            val m = map.map(h)
            val o = Offset(m.x, m.y)
            drawCircle(color = Color(0xFFFF00BF), radius = 8f, center = o)
        }
        face.eyeLine?.let { e ->
            val a = map.map(e.a)
            val b = map.map(e.b)
            drawLine(
                color = Color(0xFF66FF66),
                start = Offset(a.x, a.y),
                end = Offset(b.x, b.y),
                strokeWidth = 3f,
            )
        }
    }
}
