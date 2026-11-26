package com.openapi.app.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
 * 历史详情底部主操作渲染回归：管理动作迁入顶栏「⋯」菜单后，底部只留「再拍一题」。
 * 放在 :app 而不是 :feature-photosolve：stringResource 需要已链接资源。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "zh-rCN-w360dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryDetailActionsRenderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun onlyRetakePrimaryActionIsDisplayedOnNarrowScreen() {
        compose.setContent {
            AppMaterialTheme {
                HistoryDetailActions(onRetake = {}, modifier = Modifier.width(360.dp))
            }
        }

        compose.onNodeWithText("再拍一题").assertIsDisplayed()
    }
}
