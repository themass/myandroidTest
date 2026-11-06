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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.core.design.theme.AppColors
import com.openapi.core.net.BaseService
import com.openapi.core.net.request.CommonResponse
import com.openapi.core.net.request.InfoListVo
import com.openapi.core.util.DateUtils
import com.openapi.core.util.StringUtils
import com.openapi.core.util.ToastUtil
import com.openapi.ks.chat.R
import com.openapi.ks.myapp.bean.form.IwannaForm
import com.openapi.ks.myapp.bean.vo.IWannaVo
import com.openapi.ks.myapp.bean.vo.NullReturnVo
import com.openapi.ks.myapp.constant.Constants
import com.openapi.ks.myapp.data.UserLoginUtil
import com.openapi.ks.myapp.ui.user.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * 反馈页（抽屉 "反馈"）。原来是 CommonFragmentActivity 里的 IWannaFragment（XML + RecyclerView），现在是 Compose。
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
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Surface) {
                    FeedbackScreen(
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

/** 按页取数据（阻塞，调用方需在后台线程）。与旧版 loadData 的 URL 拼法一致。 */
private fun fetchPage(service: BaseService, pageNum: Int): InfoListVo<IWannaVo> =
    service.getInfoListData(
        String.format(Constants.getUrl(Constants.API_IWANNA_URL), pageNum),
        IWannaVo::class.java,
        FeedbackActivity.TAG,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackScreen(
    service: BaseService,
    onBack: () -> Unit,
    onNeedLogin: () -> Unit,
) {
    val context = LocalContext.current
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
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

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
        val result = runCatching { withContext(Dispatchers.IO) { fetchPage(service, 0) } }
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
            runCatching { withContext(Dispatchers.IO) { fetchPage(service, nextPage) } }
                .onSuccess { data ->
                    feed.addAll(data.voList)
                    nextPage = data.pageNum
                    hasMore = data.hasMore
                }
            loadingMore = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.iwanna_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                initialLoading && feed.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                failed && feed.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.home_channels_error), color = AppColors.TextSecondary)
                    TextButton(onClick = { reloadTick++ }) { Text(stringResource(R.string.home_channels_retry)) }
                }
                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        scope.launch {
                            val r = runCatching { withContext(Dispatchers.IO) { fetchPage(service, 0) } }
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
                                    String.format(Constants.getUrl(Constants.API_IWANNA_LIKE_URL), entry.id),
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
            onChange = { comment = it },
            onSend = {
                when {
                    UserLoginUtil.getUserCache() == null -> onNeedLogin()
                    !StringUtils.hasText(comment) -> ToastUtil.showShort(R.string.iwanna_content_error)
                    else -> {
                        sending = true
                        service.postData(
                            Constants.getUrl(Constants.API_IWANNA_URL),
                            IwannaForm(comment),
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
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = AppColors.Surface, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                placeholder = { Text(stringResource(R.string.iwanna_title)) },
                modifier = Modifier.weight(1f),
                maxLines = 3,
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSend, enabled = enabled) { Text(stringResource(R.string.send)) }
        }
    }
}
