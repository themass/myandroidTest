package com.openapi.ks.myapp.companion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 陪伴界面的完整装配。
 *
 * 抽出 Route 是为了让主页 Tab 与将来的独立入口共用同一份实现——ChatRoute
 * 也是同样的结构。默认角色先写死为林澈，选定角色的流程接上角色列表后替换。
 */
@Composable
fun CompanionRoute(
    modifier: Modifier = Modifier,
    characterId: String = DEFAULT_CHARACTER_ID,
    viewModel: CompanionViewModel = viewModel(),
) {
    CompanionScreen(
        viewModel = viewModel,
        characterId = characterId,
        modifier = modifier,
    )
}

/**
 * 默认角色。等角色选择接上后由 [com.openapi.roles] 的目录决定，这里只保证
 * 界面有一颗可用的种子角色。
 */
const val DEFAULT_CHARACTER_ID = "linche"
