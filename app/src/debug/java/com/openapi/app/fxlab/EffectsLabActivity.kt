package com.openapi.app.fxlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme

/**
 * debug-only 的特效引擎对比页容器。**只在 `app/src/debug/AndroidManifest.xml`
 * 注册**（exported=false、无 launcher category），release 清单合并不到它。
 */
class EffectsLabActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                EffectsLabScreen(onNavigateBack = { finish() })
            }
        }
    }
}