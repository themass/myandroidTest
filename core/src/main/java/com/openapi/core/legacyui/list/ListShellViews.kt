package com.openapi.core.legacyui.list

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.openapi.common.util.log.LogUtil
import com.openapi.common.ui.widget.DividerItemDecoration
import com.openapi.core.R
import com.openapi.common.R as UikitR

/** 底部“加载更多”条：灰底 + 小圈 + 文案（与原 footer 布局一致）。 */
@Composable
internal fun LoadMoreFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.base_gray))
            .padding(top = 5.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp).padding(end = 10.dp),
            color = Color.White,
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(R.string.load),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 列表状态覆盖层（Compose 版 loading / load_retry）。与 [ListShellView] 是兄弟节点，画在 content 之上。
 * [CONTENT] 时整个视图 GONE，不拦截触摸。
 */
class ListStateOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var shellState by mutableStateOf(ListShellState.LOADING)
    private var retryClick: Runnable? = null

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    fun setOnRetryClick(r: Runnable?) {
        retryClick = r
    }

    fun setState(state: ListShellState) {
        shellState = state
        visibility = if (state == ListShellState.CONTENT) View.GONE else View.VISIBLE
    }

    @Composable
    override fun Content() {
        when (shellState) {
            ListShellState.CONTENT -> Unit
            ListShellState.LOADING -> LoadingPlaceholder()
            ListShellState.RETRY -> RetryPlaceholder(
                textRes = UikitR.string.error_network_retry,
                onClick = { retryClick?.run() },
            )
            ListShellState.NODATA -> RetryPlaceholder(
                textRes = UikitR.string.error_network_nodata_retry,
                onClick = { retryClick?.run() },
            )
        }
    }
}

/** 与原 loading 视图一致：高 250dp，顶部留 55dp，水平居中 48dp 小圈。 */
@Composable
internal fun LoadingPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(top = 55.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = colorResource(R.color.style_color_accent),
        )
    }
}

/** 与原 load_retry 视图一致：整块可点击重试，文字 18sp #a8b0be 居中。 */
@Composable
internal fun RetryPlaceholder(textRes: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(top = 55.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(textRes),
                color = Color(0xFFA8B0BE),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
