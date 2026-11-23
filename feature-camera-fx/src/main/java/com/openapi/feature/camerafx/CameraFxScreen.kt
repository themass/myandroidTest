package com.openapi.feature.camerafx

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors

/**
 * demo 画面：CameraX 预览 + Compose Canvas 叠加。
 *
 * 颜色语义：骨架 = 青，面部折线 = 黄，人脸框 = 白虚线，头顶锚点 = 品红圆 + 十字，
 * 两眼连线 = 绿（倾角就是这条线的角度）。
 *
 * 绑定策略：PreviewView 实例在 factory 里存住，LaunchedEffect(provider, view) 一次性
 * bind 预览 + 分析两个 use case；每帧的 Canvas 只读 StateFlow，不会触发重绑。
 */
@Composable
fun CameraFxScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // 首次申请前必须明示：画面只在本机处理、不上传（合规红线，文案硬编码在模块里）
    var consentPending by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    if (!granted) {
        if (consentPending) {
            AlertDialog(
                onDismissRequest = { consentPending = false },
                title = { Text("摄像头定位演示") },
                text = {
                    Text(
                        "画面仅在本机实时处理，不上传服务器、不保存、不记录任何人脸数据。" +
                            "关闭本页即停止全部处理。",
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            consentPending = false
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                    ) { Text("同意并开始") }
                },
                dismissButton = {
                    TextButton(onClick = { consentPending = false }) { Text("取消") }
                },
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("没有摄像头权限，无法演示", color = Color.White, fontSize = 16.sp)
                    Button(
                        onClick = { consentPending = true },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("重试") }
                }
            }
        }
        return
    }

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
    // ViewPort 要非零宽高；记下 PreviewView 布局尺寸，变了才重绑
    var viewSize by remember { mutableStateOf(0 to 0) }
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
            // 预览与分析钉死同一 4:3 视野 + 有界分辨率：
            //  - 两个 use case 比例不一致 → 覆盖层与预览画面取景范围不同，看着就是「错位」
            //  - 分析不封顶 → 每帧全分辨率送进模型，fps 掉到 ~12（真机实测）
            // 再用 UseCaseGroup+ViewPort 把两路裁到同一 PreviewView FOV；analyzer 侧吃 cropRect。
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
                // 缓冲直接转成 target 正立；rotationDegrees=0。
                // UseCaseGroup+ViewPort 后若不启用，crop 后的帧常报 rotation=0 但内容仍是
                // sensor 横图 → MediaPipe 不转 → 倾角又回到 ~89°（真机 FOV 修复轮复现）。
                .setOutputImageRotationEnabled(true)
                .build()
                .also { it.setAnalyzer(analysisExecutor, analyzer) }
            val viewPort = view.viewPort
                ?: view.getViewPort(preview.targetRotation)
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    // PERFORMANCE 模式下 getViewPort 可能不准；COMPATIBLE 保证 ViewPort=所见
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
        if (fx != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawFx(fx)
            }
        }

        // 状态行：帧率 + 检测数 + 隐私提示
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color(0x99000000))
                .padding(8.dp),
        ) {
            val f = frame
            val faceN = f?.faces?.size ?: 0
            val poseN = f?.poses?.count { it.segments.isNotEmpty() } ?: 0
            val tilt = f?.faces?.firstOrNull()?.tiltDeg
            Text(
                "fx ${f?.fps?.let { "%.1f fps".format(it) } ?: "--"} · 脸 $faceN · 姿态 $poseN" +
                    (if (tilt != null) " · 倾角 %.1f°".format(tilt) else "") +
                    (f?.let { " · ${it.imgW}x${it.imgH}" } ?: ""),
                color = Color.White,
                fontSize = 13.sp,
            )
            Text("本机处理 · 不上传 · 不落盘", color = Color(0xFF9FFF9F), fontSize = 11.sp)
            val err = fatal
            if (err != null) {
                Text(err, color = Color(0xFFFF8080), fontSize = 12.sp)
            }
        }
    }
}

/** 在画布上画一帧：纯映射 + 画线；所有计算都在纯函数层（有 JVM 单测钉死）。 */
private fun DrawScope.drawFx(fx: FrameFx) {
    val map = CanvasMap(
        canvasW = size.width,
        canvasH = size.height,
        imgW = fx.imgW,
        imgH = fx.imgH,
        fill = true,
        // 前摄 + CameraX 1.4.2：预览默认镜像显示，关键点来自未镜像的分析图 → 归一化空间翻 x
        mirrorX = true,
    )

    // 姿态骨架（先画，脸叠在上面）
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

    for (face in fx.faces) {
        // 面部稀疏折线（外廓/眉/眼/唇，闭合）
        val path = Path()
        for (line in face.polylines) {
            if (line.size < 2) continue
            val first = map.map(line.first())
            path.moveTo(first.x, first.y)
            for (p in line.drop(1)) {
                val o = map.map(p)
                path.lineTo(o.x, o.y)
            }
            path.close()
        }
        drawPath(path, color = Color(0xFFFFEB3B), style = Stroke(width = 2.5f))

        // 人脸框（白虚线）
        face.bounds?.let { b ->
            val tl = map.map(Point2d(b.x, b.y))
            val br = map.map(Point2d(b.x + b.width, b.y + b.height))
            drawRect(
                color = Color.White,
                topLeft = Offset(minOf(tl.x, br.x), minOf(tl.y, br.y)),
                size = Size(abs(br.x - tl.x), abs(br.y - tl.y)),
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
            )
        }

        // 头顶锚点：品红实心圆 + 十字（sticker 挂点示意）
        face.headTop?.let { h ->
            val m = map.map(h)
            val o = Offset(m.x, m.y)
            drawCircle(color = Color(0xFFFF00BF), radius = 8f, center = o)
            drawLine(Color(0xFFFF00BF), Offset(o.x - 18f, o.y), Offset(o.x + 18f, o.y), 2f)
            drawLine(Color(0xFFFF00BF), Offset(o.x, o.y - 18f), Offset(o.x, o.y + 18f), 2f)
        }

        // 两眼连线（倾角）
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

private fun abs(v: Float): Float = if (v < 0f) -v else v
