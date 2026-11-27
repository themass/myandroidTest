package com.openapi.app.teaching

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.openapi.ks.myapp.companion.teaching.TeachingDemoRoute
import com.openapi.ks.myapp.companion.teaching.TeachingSpeech

/**
 * 陪伴教学 Demo v2 的 Activity 壳（debug-only）。
 *
 * 放在 `:app` 的 debug 源集（与 fxlab 同路）是**架构要求**：
 * Activity 不能接收 lambda，而学生摄像头内容必须由组合根注入
 * （ModuleGraphTest：feature 互不依赖，`:feature-companion` 不 import `:feature-camera-fx`）。
 * 本壳只做三件事：持有 [TeachingSpeech]（TTS→包络→唇形，不申请麦克风）、
 * 把 `CameraFxGrantedContent` 注进 Route 的摄像头槽、其余 UI 全在 TeachingDemoRoute。
 *
 * 设计边界见 docs/design/companion-teaching-demo/redesign-v2-stage-camera.md。
 */
class TeachingDemoActivity : ComponentActivity() {

    private lateinit var speech: TeachingSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speech = TeachingSpeech(this)
        setContent {
            MaterialTheme {
                TeachingDemoRoute(
                    speak = speech::speak,
                    mouthLevel = speech.mouthLevel,
                    speechError = speech.error,
                    onExit = { finish() },
                    studentCamera = { modifier, praiseActive ->
                        // 摄像头内容注入：与 HomeScreen 给通话页接人脸小窗一模一样。
                        // 表扬时贴纸换皇冠（庆祝），平时爱心——都走贴纸白名单，
                        // 不再借用舞台特效词（用户 2026-11-28：贴纸与特效是两套词表）。
                        com.openapi.feature.camerafx.CameraFxGrantedContent(
                            modifier = modifier,
                            overlayPolicy = com.openapi.feature.camerafx.CameraFxOverlayPolicy(
                                drawWireframe = false,
                                drawStickers = true,
                                showDebugHud = false,
                                // 词表在 StickerAssetIds 白名单内。
                                stickerAssetId = if (praiseActive)
                                    com.openapi.feature.camerafx.StickerAssetIds.CROWN
                                else
                                    com.openapi.feature.camerafx.StickerAssetIds.PAT_HEAD,
                            ),
                        )
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        speech.shutdown()
        super.onDestroy()
    }
}
