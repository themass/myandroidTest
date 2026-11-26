package com.openapi.app.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.openapi.common.ui.theme.AppMaterialTheme
import com.openapi.feature.photosolve.ui.HistoryDetailActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 历史详情底部操作行的渲染回归（用户反馈：「历史…下面的按钮布局太丑，都换行了」）。
 *
 * 修的是什么：旧实现是四个等宽 OutlinedButton 塞一行，「取消收藏」四个汉字加
 * 默认 24dp 横向 contentPadding，在 360dp 窄屏上每个按钮的文字区只剩 ~40dp，
 * 标签必然折成两行。修复把收藏改成纯星标图标（切换态靠实心/空心），其余三项
 * `maxLines = 1 + softWrap = false`，「再拍一题」weight(1f) 吃掉剩余宽度。
 *
 * 与 QuadAdjustButtonTextRenderTest 同一口径：Robolectric 字体度量与真机差得多，
 * 不断言行高几何；`softWrap = false` 结构上已禁掉换行，这里守的是可见契约——
 * 窄屏 360dp 上所有操作都完整显示、不被挤出屏幕。
 *
 * 放在 :app 而不是 :feature-photosolve：库模块开 includeAndroidResources 会资源
 * 链接失败，stringResource 需要真实值（见同目录先例说明）。
 */
@RunWith(RobolectricTestRunner::class)
// 窄屏窗口：360dp 是换行最容易出现的宽度（先例：QuadAdjustButtonTextRenderTest）
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w360dp-h891dp",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryDetailActionsRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(favorite: Boolean) {
        compose.setContent {
            AppMaterialTheme {
                HistoryDetailActions(
                    favorite = favorite,
                    onFavorite = {},
                    onRename = {},
                    onDelete = {},
                    onRetake = {},
                    modifier = Modifier.width(360.dp),
                )
            }
        }
    }

    /** 未收藏态：三个文字按钮 + 收藏星标在 360dp 窄屏上都必须完整显示。 */
    @Test
    fun allActionsDisplayedOnNarrowScreen() {
        render(favorite = false)

        compose.onNodeWithContentDescription("收藏").assertIsDisplayed()
        for (label in listOf("重命名", "删除", "再拍一题")) {
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    /** 已收藏态：图标换实心星，无障碍标签跟着切到「取消收藏」，不引入折行的文字按钮。 */
    @Test
    fun favoriteStateKeepsRowOnOneLine() {
        render(favorite = true)

        compose.onNodeWithContentDescription("取消收藏").assertIsDisplayed()
        for (label in listOf("重命名", "删除", "再拍一题")) {
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }
}
