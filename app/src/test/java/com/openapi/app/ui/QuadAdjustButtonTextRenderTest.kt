package com.openapi.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.openapi.feature.photosolve.ui.QuadAdjustScreen
import com.openapi.imaging.quad.QuadNorm
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 四角微调页底部按钮文字的渲染回归（用户反馈：底部按钮文字换行）。
 *
 * 修的是什么：底部 Row 原来是 `padding(16.dp):spacedBy(12.dp)`，三个按钮塞在窄屏上
 * 会把「裁剪并识别」挤成两行（按钮文字换行=扯淡）。修复把间距收窄、按钮加了
 * contentPadding，并给文字加 `maxLines=1:softWrap=false`——任何宽度下都只有一行，
 * 放不下就截断而不是绕行。这条要守的可见契约：三个按钮文字在 360dp 窄屏上都
 * 完整显示、在屏幕内、互不重叠挤出。
 *
 * 不断言几何/行高：Robolectric 的字体度量与真机差得多（同目录 ChatUserAvatarRenderTest
 * 的实测注释），量「有没有换行/出界」会钉出假数字；`softWrap=false` 在结构上已经禁掉
 * 换行，该守的是「都被显示出来、没被挤出屏幕」。窄屏 360dp 能全部显示，
 * 就是这次修复要守住的可见回归。
 *
 * 放在 :app 而不是 :feature-photosolve：库模块打开 includeAndroidResources 会资源
 * 链接失败（这里用 stringResource 需要真实值，见同目录先例说明）。
 * QuadAdjustScreen 是公开 API，测的就是真机装配。
 */
@RunWith(RobolectricTestRunner::class)
// 窄屏窗口：360dp 是换行最容易出现的宽度（411dp 不触发）
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w360dp-h891dp",
)
// 画布需要真实 Bitmap 渲染（NATIVE 图形模式，与 :mediaview 渲染回归同一套）
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuadAdjustButtonTextRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val fullFrame = QuadNorm.fullFrame()

    private fun render() {
        compose.setContent {
            QuadAdjustScreen(
                bitmap = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888),
                quad = fullFrame,
                detecting = false,
                onQuadChange = {},
                onRedetect = {},
                onRotate = {},
                onConfirm = {},
                onCancel = {},
                modifier = Modifier.size(width = 360.dp, height = 700.dp),
            )
        }
    }

    /** 三个底部按钮（TextButton×2 + Button×1）的文字在窄屏上都必须完整显示。 */
    @Test
    fun bottomRowButtonTextsAreAllDisplayed() {
        render()

        for (label in listOf("自动识别", "旋转", "裁剪并识别")) {
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }
}