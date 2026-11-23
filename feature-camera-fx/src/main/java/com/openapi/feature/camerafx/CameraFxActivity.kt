package com.openapi.feature.camerafx

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme

/**
 * 摄像头定位 demo 的入口 Activity（仅 debug 入口可达，见 app 侧 LabGate）。
 *
 * 隐私红线：本 Activity 不上传任何帧、不落盘、不记录人脸数据。
 * 生命周期结束（finish）时分析线程与 native 模型全部释放。
 */
class CameraFxActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                CameraFxScreen(onNavigateBack = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CameraFxActivity::class.java))
        }
    }
}
