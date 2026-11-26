package com.openapi.app.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.openapi.common.ui.theme.AppMaterialTheme
import com.openapi.feature.photosolve.presentation.PhotoSolveResult
import com.openapi.feature.photosolve.ui.PhotoResultScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 结果页正文（SolveResultSections）的渲染回归（5ace4ae3 重构守门）。
 *
 * 这次把结果页正文从 PhotoResultScreen 内联代码抽成 SolveResultSections，让
 * 历史详情页走同一份。重构最容易出的事是「某个区块在抽的时候丢了」——这里在
 * 360dp 窄屏上把四个区块的标题（识别题目 / 思考过程 / 解题步骤 / 最终答案）
 * 全部断言存在，正文本身走 MathText（AndroidView，不进 Compose 语义树），
 * 所以断言的是 Compose 侧的标签与折叠区标题。
 *
 * 用 assertExists 而不是 assertIsDisplayed：页面是 verticalScroll 的 Column，
 * 全部子项都会参与组合，但折叠区以下的节点未必在视口内（见同目录先例对
 * Robolectric 几何的告诫）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w360dp-h891dp",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoResultSectionsRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val result = PhotoSolveResult(
        recognizedQuestion = "小明有 5 个苹果，又买了 3 个，一共有几个？",
        steps = listOf("先数原来的 5 个", "再加上新买的 3 个"),
        finalAnswer = "8 个",
        thinking = "这是一道加法题，把两部分合起来数",
        subject = "math",
        requestId = "verify-r1",
    )

    @Test
    fun allSectionsRenderAfterSharedBodyRefactor() {
        compose.setContent {
            AppMaterialTheme {
                PhotoResultScreen(
                    croppedBitmap = null,
                    result = result,
                    onRetake = {},
                    onExit = {},
                    onOpenHistory = {},
                    modifier = Modifier.width(360.dp),
                )
            }
        }

        for (label in listOf("识别题目", "思考过程", "解题步骤", "最终答案")) {
            compose.onNodeWithText(label).assertExists()
        }
        // 折叠摘要默认显示：不展开也能看到思考过程的第一句
        compose.onNodeWithText("这是一道加法题，把两部分合起来数").assertExists()
    }
}
