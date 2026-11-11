package com.openapi.ks.myapp.ui.feedback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.common.design.theme.AppColors
import com.openapi.common.net.http.BaseService
import com.openapi.common.net.awaitInfoList
import com.openapi.common.net.request.CommonResponse
import com.openapi.common.net.request.InfoListVo
import com.openapi.common.util.lang.DateUtils
import com.openapi.common.util.lang.StringUtils
import com.openapi.common.util.android.ToastUtil
import com.openapi.feature.user.R
import com.openapi.ks.myapp.bean.form.IwannaForm
import com.openapi.ks.myapp.bean.vo.IWannaVo
import com.openapi.core.model.NullReturnVo
import com.openapi.core.config.Constants
import com.openapi.core.account.UserLoginUtil
import com.openapi.ks.myapp.ui.user.login.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import com.openapi.common.design.theme.AppMaterialTheme

/**
 * 反馈页（抽屉 "反馈"）。恢复了旧版 FeedbackChooseFragment 的两个分类，顺序与旧版一致：
 * - 想要（旧版 IWannaFragment）：列表与发送都走 [Constants.API_IWANNA_URL]。
 * - 积分充值反馈（旧版 FeedbackFragment）：列表与点赞走 [Constants.API_FEEDBACK_URL] /
 *   [Constants.API_FEEDBACK_LIKE_URL]；发送也走 [Constants.API_FEEDBACK_URL]，正文为
 *   "<输入内容>--<充值方式>"，充值方式取自 R.array.recharge_arry，与旧版一致。
 *
 * 行为保持：下拉刷新、上拉加载、点赞（每点一次加一，与旧版一致）、发送需要登录、
 * 已处理或管理员的条目显示勾。旧版点赞时的心形飘动动画是纯视觉效果，这里没有保留。
 */
class FeedbackActivity : ComponentActivity() {

    private val baseService = BaseService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        baseService.setup(this)
        setContent {
            AppMaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Surface) {
                    FeedbackRoot(
                        service = baseService,
                        onBack = { finish() },
                        onNeedLogin = { startActivity(Intent(this, LoginActivity::class.java)) },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        baseService.cancelRequest(TAG)
        super.onDestroy()
    }

    companion object {
        const val TAG = "IWannaFragment"

        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, FeedbackActivity::class.java))
        }
    }
}

/** 两个反馈分类，顺序与旧版 FeedbackChooseFragment 一致（先 想要，后 积分充值反馈）。 */
private enum class FeedbackCategory(
    val tabTitleRes: Int,
    val listUrl: String,
    val likeUrl: String,
    val sendUrl: String,
    val withRecharge: Boolean,
) {
    WANNA(
        tabTitleRes = R.string.iwanna_title,
        listUrl = Constants.API_IWANNA_URL,
        likeUrl = Constants.API_IWANNA_LIKE_URL,
        sendUrl = Constants.API_IWANNA_URL,
        withRecharge = false,
    ),
    RECHARGE(
        tabTitleRes = R.string.money_back_title,
        listUrl = Constants.API_FEEDBACK_URL,
        likeUrl = Constants.API_FEEDBACK_LIKE_URL,
        sendUrl = Constants.API_FEEDBACK_URL,
        withRecharge = true,
    ),
}

/** 按页取数据（挂起）。与旧版 loadData 的 URL 拼法一致。 */
private suspend fun fetchPage(service: BaseService, listUrl: String, pageNum: Int): InfoListVo<IWannaVo> =
    service.awaitInfoList(
        String.format(Constants.getUrl(listUrl), pageNum),
        IWannaVo::class.java,
        FeedbackActivity.TAG,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackRoot(
    service: BaseService,
    onBack: () -> Unit,
    onNeedLogin: () -> Unit,
) {
    val categories = FeedbackCategory.entries
    val pagerState = rememberPagerState(pageCount = { categories.size })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.feed_back)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
        )
        TabRow(selectedTabIndex = pagerState.currentPage) {
            categories.forEachIndexed { index, category ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(stringResource(category.tabTitleRes)) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            FeedbackCategoryPage(
                category = categories[page],
                service = service,
                onNeedLogin = onNeedLogin,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackCategoryPage(
    category: FeedbackCategory,
    service: BaseService,
    onNeedLogin: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val feed = remember { mutableStateListOf<IWannaVo>() }
    var nextPage by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var initialLoading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var rechargeIndex by remember { mutableIntStateOf(0) }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val rechargeOptions = stringArrayResource(R.array.recharge_arry)

    fun applyFresh(data: InfoListVo<IWannaVo>) {
        feed.clear()
        feed.addAll(data.voList)
        nextPage = data.pageNum
        hasMore = data.hasMore
        failed = false
    }

    // 首次进入、以及发送成功后重新加载
    LaunchedEffect(reloadTick) {
        initialLoading = true
        val result = runCatching { withContext(Dispatchers.IO) { fetchPage(service, category.listUrl, 0) } }
        initialLoading = false
        result.onSuccess { applyFresh(it) }
            .onFailure { failed = feed.isEmpty() }
    }

    // 滚到末尾附近时加载下一页
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            feed.isNotEmpty() && last >= feed.size - 3
        }
    }
    LaunchedEffect(nearEnd, hasMore) {
        if (nearEnd && hasMore && !loadingMore && !refreshing && !initialLoading) {
            loadingMore = true
            runCatching { withContext(Dispatchers.IO) { fetchPage(service, category.listUrl, nextPage) } }
                .onSuccess { data ->
                    feed.addAll(data.voList)
                    nextPage = data.pageNum
                    hasMore = data.hasMore
                }
            loadingMore = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                initialLoading && feed.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                failed && feed.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.feedback_load_error), color = AppColors.TextSecondary)
                    TextButton(onClick = { reloadTick++ }) { Text(stringResource(R.string.home_channels_retry)) }
                }
                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        scope.launch {
                            val r = runCatching { withContext(Dispatchers.IO) { fetchPage(service, category.listUrl, 0) } }
                            refreshing = false
                            r.onSuccess { applyFresh(it) }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(feed, key = { it.id }) { entry ->
                            FeedCard(entry, onLike = {
                                entry.like = true
                                entry.likes++
                                service.postData(
                                    String.format(Constants.getUrl(category.likeUrl), entry.id),
                                    null, null, null, FeedbackActivity.TAG, NullReturnVo::class.java,
                                )
                            })
                        }
                        if (loadingMore) {
                            item { CircularProgressIndicator(Modifier.padding(8.dp).size(24.dp)) }
                        }
                    }
                }
            }
        }
        CommentBar(
            value = comment,
            enabled = !sending,
            withRecharge = category.withRecharge,
            rechargeOptions = rechargeOptions,
            rechargeIndex = rechargeIndex,
            onRechargeSelected = { rechargeIndex = it },
            onChange = { comment = it },
            onSend = {
                when {
                    UserLoginUtil.getUserCache() == null -> onNeedLogin()
                    !StringUtils.hasText(comment) -> ToastUtil.showShort(R.string.iwanna_content_error)
                    else -> {
                        sending = true
                        // 充值分类：正文为 "<输入内容>--<充值方式>"，与旧版 FeedbackFragment 一致
                        val content = if (category.withRecharge) {
                            "$comment--${rechargeOptions[rechargeIndex]}"
                        } else {
                            comment
                        }
                        service.postData(
                            Constants.getUrl(category.sendUrl),
                            IwannaForm(content),
                            object : CommonResponse.ResponseOkListener<IWannaVo>() {
                                override fun onResponse(o: IWannaVo) {
                                    sending = false
                                    comment = ""
                                    reloadTick++
                                }
                            },
                            object : CommonResponse.ResponseErrorListener() {
                                override fun onError() {
                                    sending = false
                                }
                            },
                            FeedbackActivity.TAG,
                            IWannaVo::class.java,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun FeedCard(entry: IWannaVo, onLike: () -> Unit) {
    val showOk = entry.finished || Constants.ADMIN == entry.name
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.name.orEmpty(),
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (showOk) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AppColors.Success, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(entry.content.orEmpty(), color = AppColors.TextPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(entry.appName?.takeIf { it.isNotBlank() }, entry.where?.takeIf { it.isNotBlank() })
                        .joinToString(" · "),
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Image(
                    painter = painterResource(if (entry.like) R.drawable.ic_heart_small_red else R.drawable.ic_heart_small_blue),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).clickable(onClick = onLike),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    pluralStringResource(R.plurals.likes_count, entry.likes, entry.likes),
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Text(
                DateUtils.format(Date(entry.time), DateUtils.DATETIME_FORMAT),
                color = AppColors.TextSecondary,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CommentBar(
    value: String,
    enabled: Boolean,
    withRecharge: Boolean,
    rechargeOptions: Array<String>,
    rechargeIndex: Int,
    onRechargeSelected: (Int) -> Unit,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = AppColors.Surface, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (withRecharge) {
                Text(stringResource(R.string.recharge), color = AppColors.TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
            }
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                placeholder = {
                    Text(stringResource(if (withRecharge) R.string.feedback_hint else R.string.iwanna_title))
                },
                modifier = Modifier.weight(1f),
                maxLines = 3,
            )
            if (withRecharge) {
                RechargePicker(
                    options = rechargeOptions,
                    selected = rechargeIndex,
                    enabled = enabled,
                    onSelected = onRechargeSelected,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSend, enabled = enabled) { Text(stringResource(R.string.send)) }
        }
    }
}

/** 旧版 sp_recharge 下拉框的等价实现：显示当前充值方式，点开选择。 */
@Composable
private fun RechargePicker(
    options: Array<String>,
    selected: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text(options[selected], color = AppColors.TextPrimary, fontSize = 14.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    },
                )
            }
        }
    }
}
