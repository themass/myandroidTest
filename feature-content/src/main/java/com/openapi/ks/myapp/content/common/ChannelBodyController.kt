package com.openapi.ks.myapp.content.common

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.openapi.network.http.BaseService
import com.openapi.network.request.InfoListVo
import com.openapi.network.awaitInfoList
import com.openapi.common.util.log.LogUtil
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.common.util.lang.StringUtils
import com.openapi.core.model.RecommendVo
import com.openapi.core.config.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which body the channel screen shows; mirrors the legacy LoadableFragment views. */
enum class ChannelUiState { Loading, Content, Retry, NoData }

/**
 * Paging/loading state for a channel body screen. Owned by the fragment so it survives view
 * re-creation; behaviour mirrors LoadableFragment + BasePullLoadbleFragment + MyPullView.
 */
class ChannelBodyController(
    private val appContext: Context,
    val service: BaseService,
    private val netTag: String,
    private val urlFor: (Int) -> String,
) {
    val items = mutableStateListOf<RecommendVo>()

    var uiState by mutableStateOf(ChannelUiState.Loading)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set

    /** Equivalent of MyPullView.needLoad() / InfoListVo.hasMore. */
    var hasMore = false
        private set

    private var pageNum = 0
    private var loaded = false
    private var job: Job? = null

    /** onResume: load only if nothing has been shown yet (legacy: mData == null). */
    fun ensureLoaded(scope: CoroutineScope) {
        if (!loaded) startQuery(scope, showLoading = true)
    }

    /** Retry view tap or no-data tap (legacy startQuery(true), same page index). */
    fun retry(scope: CoroutineScope) {
        startQuery(scope, showLoading = true)
    }

    /** Pull to refresh (legacy FRESH: pageNum = 0, startQuery(false)). */
    fun pullToRefresh(scope: CoroutineScope) {
        if (loadingMore) {
            refreshing = false
            return
        }
        pageNum = 0
        refreshing = true
        startQuery(scope, showLoading = false)
    }

    /** Scroll near the end (legacy LOADMORE, guarded by footer/refresh/needLoad). */
    fun loadMore(scope: CoroutineScope) {
        if (loadingMore || refreshing || !hasMore) return
        loadingMore = true
        startQuery(scope, showLoading = false)
    }

    private fun startQuery(scope: CoroutineScope, showLoading: Boolean) {
        job?.cancel()
        if (showLoading) uiState = ChannelUiState.Loading
        job = scope.launch {
            val data: InfoListVo<RecommendVo>? = try {
                service.awaitInfoList(urlFor(pageNum), RecommendVo::class.java, netTag)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(e)
                null
            }
            ensureActive()
            onResult(data)
        }
    }

    private fun onResult(data: InfoListVo<RecommendVo>?) {
        if (data == null) {
            // Legacy: hideLoading(); if never loaded show retry, otherwise keep content.
            loadingMore = false
            refreshing = false
            uiState = if (loaded) ChannelUiState.Content else ChannelUiState.Retry
            return
        }
        val page: List<RecommendVo> = data.voList ?: emptyList()
        // Legacy: setData(rawPage) shows the no-data view for an empty page. The
        // legacy code never switches back to content after this point, so an empty
        // load-more page also hides the list. That quirk is kept on purpose.
        uiState = if (page.isEmpty()) ChannelUiState.NoData else ChannelUiState.Content

        applyPictureSwitch(page)
        if (loadingMore) {
            items.addAll(page)
        } else {
            items.clear()
            items.addAll(page)
        }
        assignDefaultColors()
        hasMore = data.hasMore
        pageNum = data.pageNum
        loaded = true
        if (items.isEmpty()) uiState = ChannelUiState.NoData
        loadingMore = false
        refreshing = false
    }

    /** Legacy IndexRecommendAdapter.animatePhoto: hide pictures when the setting is off. */
    private fun applyPictureSwitch(page: List<RecommendVo>) {
        val picShow = PreferenceUtils.getPrefBoolean(appContext, Constants.VIDEO_PIC_SWITCH, true)
        if (picShow) return
        for (vo in page) {
            if (vo.dataType == RecommendVo.dataType_VIDEO_CHANNEL || vo.dataType == RecommendVo.dataType_IMG_CHANNEL) {
                vo.showType = Constants.ShowType.Normal
                vo.img = ""
                if (vo.rate >= 1) vo.rate = 0.8f
            }
        }
    }

    /** Legacy adapter: a missing color gets colorBg[position % size], keyed by list index. */
    private fun assignDefaultColors() {
        val colors = Constants.colorBg
        for (i in items.indices) {
            val vo = items[i]
            if (!StringUtils.hasText(vo.color)) {
                vo.color = colors[i % colors.size]
            }
        }
    }
}
