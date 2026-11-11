package com.openapi.core.legacyui.gsy

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
import com.openapi.common.view.view.DividerItemDecoration
import com.openapi.core.R
import com.openapi.common.R as CoreR

/** 列表页的四种外观，对应原 LoadableFragment 里 loading / load_retry / content 三个视图的组合。 */
enum class ListShellState { LOADING, RETRY, NODATA, CONTENT }

/**
 * 列表外壳（Compose 版 MyPullView）：下拉刷新 + 底部“加载更多”条 + 原生 RecyclerView。
 *
 * 列表本体仍是 RecyclerView，通过 [AndroidView] 嵌入，所以各子页面的 adapter、布局管理器、
 * GSY 视频播放器（挂在卡片里的原生 FrameLayout）保持原样。
 *
 * 行为与原 MyPullView 保持一致：
 * - 下拉：若底部加载条可见（正在加载更多）则只收起刷新圈，不发起刷新；否则 FRESH。
 * - 上滑分页：dy>0 且未在加载更多、未在刷新时，最后可见项接近末尾且 needLoad() 为真则 LOADMORE 并显示加载条。
 * - notifyDataSetChanged：收起刷新圈、隐藏加载条、通知 adapter。
 */
@OptIn(ExperimentalMaterial3Api::class)
class ListShellView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    interface OnRefreshListener {
        fun onRefresh(type: Int)
        fun needLoad(): Boolean
    }

    companion object {
        const val FRESH = 1
        const val LOADMORE = 2
    }

    private var listener: OnRefreshListener? = null
    private var refreshing by mutableStateOf(false)
    private var loadingMore by mutableStateOf(false)

    /** 与原 MyPullView 一样：两条分割线装饰（MyPullView 自带一条，BasePullLoadbleFragment 再加一条）。 */
    val recyclerView: RecyclerView = RecyclerView(context).apply {
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL, CoreR.drawable.divider_item))
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                try {
                    if (dy > 0 && !isLoadMore() && !refreshing) {
                        val count = rv.adapter!!.itemCount
                        val lm = rv.layoutManager
                        if (lm is StaggeredGridLayoutManager) {
                            val visibleItems = lm.findLastVisibleItemPositions(null)
                            val lastItem = maxOf(visibleItems[0], visibleItems[1])
                            if (lastItem > count - 5 && listener?.needLoad() == true) {
                                loadData(LOADMORE)
                                showLoadingLabel()
                            }
                        } else if (lm is LinearLayoutManager) {
                            if (lm.findLastVisibleItemPosition() > count - 3 && listener?.needLoad() == true) {
                                loadData(LOADMORE)
                                showLoadingLabel()
                            }
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e("", e)
                }
            }
        })
    }

    init {
        // 与原 RecyclerView 卡片一致：不随列表回收而销毁组合，否则 GSY 原生播放器会被断开；只在 Fragment 视图销毁时释放。
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    fun setListener(l: OnRefreshListener?) {
        listener = l
    }

    fun setLayoutManager(layout: RecyclerView.LayoutManager?) {
        recyclerView.layoutManager = layout
    }

    fun setItemAnimator(animator: RecyclerView.ItemAnimator?) {
        recyclerView.itemAnimator = animator
    }

    fun setAdapter(adapter: RecyclerView.Adapter<*>?) {
        recyclerView.adapter = adapter
    }

    fun setRefresh(flag: Boolean) {
        refreshing = flag
        if (flag) {
            loadData(FRESH)
        }
    }

    fun isRefreshing(): Boolean = refreshing

    fun isLoadMore(): Boolean = loadingMore

    fun showLoadingLabel() {
        loadingMore = true
    }

    fun hindLoadingLabel() {
        loadingMore = false
    }

    fun notifyDataSetChanged() {
        refreshing = false
        hindLoadingLabel()
        recyclerView.adapter!!.notifyDataSetChanged()
    }

    private fun loadData(type: Int) {
        val l = listener
        if (l != null) {
            l.onRefresh(type)
        } else {
            refreshing = false
            hindLoadingLabel()
        }
    }

    private fun onPullRefresh() {
        if (!isLoadMore()) {
            refreshing = true
            loadData(FRESH)
        } else {
            // 加载更多期间下拉：只收起刷新圈（与原 SwipeRefresh 行为一致）
            refreshing = false
        }
    }

    @Composable
    override fun Content() {
        val isRefreshingNow = refreshing
        val isLoadingMoreNow = loadingMore
        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isRefreshingNow,
                onRefresh = { onPullRefresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                AndroidView(
                    factory = { recyclerView },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AnimatedVisibility(
                visible = isLoadingMoreNow,
                modifier = Modifier.align(Alignment.BottomCenter),
                // 原 footer 显示时做上滑动画，隐藏时直接 GONE（无动画）
                enter = slideInVertically(animationSpec = tween(durationMillis = 500)) { it },
                exit = ExitTransition.None,
            ) {
                LoadMoreFooter()
            }
        }
    }
}

/** 底部“加载更多”条：灰底 + 小圈 + 文案（与原 footer 布局一致）。 */
@Composable
private fun LoadMoreFooter() {
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
                textRes = CoreR.string.error_network_retry,
                onClick = { retryClick?.run() },
            )
            ListShellState.NODATA -> RetryPlaceholder(
                textRes = CoreR.string.error_network_nodata_retry,
                onClick = { retryClick?.run() },
            )
        }
    }
}

/** 与原 loading 视图一致：高 250dp，顶部留 55dp，水平居中 48dp 小圈。 */
@Composable
private fun LoadingPlaceholder() {
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
private fun RetryPlaceholder(textRes: Int, onClick: () -> Unit) {
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
