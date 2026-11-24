package com.openapi.app.fxlab

import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Activity 用了 enableEdgeToEdge()：内容会画到系统栏底下（真机踩过——「停」按钮被
 * 三键导航栏切掉）。这个测试把导航栏 inset 注入 ComposeView，断言 cue 按钮行
 * 完全在导航栏之上。没有 safeDrawing 处理时 bottom 会越过 inset 线，测试红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "zh-rCN-w411dp-h891dp")
class EffectsLabInsetsTest {

    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun cueButtonsStayAboveNavigationBarInset() {
        compose.setContent { MaterialTheme { EffectsLabScreen() } }
        compose.waitForIdle()
        val composeView = requireNotNull(findComposeView(compose.activity.window.decorView))
        val navPx = (48 * composeView.resources.displayMetrics.density).toInt()

        val inset = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.navigationBars(), android.graphics.Insets.of(0, 0, 0, navPx))
            .build()
        composeView.dispatchApplyWindowInsets(inset)
        compose.waitForIdle()

        val stopBottom = compose.onNodeWithText("停").fetchSemanticsNode().boundsInWindow.bottom
        assertTrue(
            "cue row bottom ${stopBottom} must clear nav inset $navPx (view height ${composeView.height})",
            stopBottom <= composeView.height - navPx,
        )
    }

    private fun findComposeView(view: View): View? {
        if (view is ComposeView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findComposeView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
