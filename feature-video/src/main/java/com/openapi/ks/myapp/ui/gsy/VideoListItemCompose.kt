package com.openapi.ks.myapp.ui.gsy

import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openapi.core.design.theme.AppColors

/** 列表卡片的文字状态：绑定时由 adapter 写入。 */
class VideoListItemState {
    var title by mutableStateOf("")
    var desc by mutableStateOf("")
}

/**
 * 把卡片绑定到 [ComposeView]。
 *
 * 封面区域放的是原生的 [container]（GSY 播放器会挂进去）和 [playButton]（GSY 会控制它的显隐，
 * 点击由 adapter 设置），所以这两个 View 必须保持原生，只用 AndroidView 嵌进来。
 * 封面按 16:9 撑开，和原来 XML 里的设计一致。
 */
fun ComposeView.bindVideoListItem(
    state: VideoListItemState,
    container: FrameLayout,
    playButton: ImageView,
) {
    // 卡片被 RecyclerView 回收时不销毁组合，否则原生播放器会被断开；只在 Fragment 视图销毁时释放
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        MaterialTheme {
            VideoListItem(state, container, playButton)
        }
    }
}

@Composable
private fun VideoListItem(
    state: VideoListItemState,
    container: FrameLayout,
    playButton: ImageView,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
        ) {
            AndroidView(factory = { container }, modifier = Modifier.fillMaxSize())
            AndroidView(
                factory = { playButton },
                modifier = Modifier.align(Alignment.Center).size(52.dp),
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = state.title,
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.desc.isNotEmpty()) {
                Text(
                    text = state.desc,
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
