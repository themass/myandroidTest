package com.openapi.app.fxlab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.openapi.ks.myapp.avatar.AvatarPackStore
import com.openapi.ks.myapp.avatar.BundledAvatars
import com.openapi.ks.myapp.avatar.LayeredAvatar
import com.openapi.ks.myapp.avatar.LayeredAvatarPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 数字人互动/教学场景：随包内置的真实分层立绘 + Lottie 效果兄弟层。
 *
 * cue 只有一个来源——页面控件（手动按钮与教学「下一步」都写同一个
 * `LabUiState.gestureId/gestureSeq` 槽位），这里只读不改，所以不存在第二条触发路径。
 * 不实现 `pointerInput`：渲染层触摸穿透。
 */
@Composable
fun StageScene(
    state: LabUiState,
    pack: LayeredAvatarPack,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(2f / 3f),
        ) {
            LayeredAvatar(
                pack = pack,
                mouthLevel = { 0f },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 引擎选择集中在 LabEffectLayer：Unavailable 只显示状态文本，绝不借用别的引擎的画面。
        LabEffectLayer(state = state, modifier = Modifier.fillMaxSize())
    }
}

/**
 * 异步加载内置示例形象。加载中或校验失败都不画半个人，页面明确显示「形象未安装」。
 */
@Composable
fun BundledStageScene(
    state: LabUiState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pack by remember { mutableStateOf<LayeredAvatarPack?>(null) }
    LaunchedEffect(context) {
        val loaded = withContext(Dispatchers.IO) {
            val store = AvatarPackStore(context)
            runCatching { BundledAvatars.ensureDemo(context, store) }
            store.loadLayered(BundledAvatars.DEMO_KEY)
        }
        pack = loaded
    }
    Box(modifier = modifier) {
        val loadedPack = pack
        if (loadedPack == null) {
            androidx.compose.material3.Text(
                text = "形象未安装",
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            StageScene(state = state, pack = loadedPack, modifier = Modifier.fillMaxSize())
        }
    }
}
