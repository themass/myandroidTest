package com.openapi.feature.user.feedback

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
import com.openapi.common.ui.theme.AppColors
import com.openapi.common.net.http.BaseService
import com.openapi.common.net.awaitInfoList
import com.openapi.common.net.request.CommonResponse
import com.openapi.common.net.request.InfoListVo
import com.openapi.common.util.lang.DateUtils
import com.openapi.common.util.lang.StringUtils
import com.openapi.common.util.android.ToastUtil
import com.openapi.feature.user.R
import com.openapi.feature.user.data.form.IwannaForm
import com.openapi.feature.user.data.vo.IWannaVo
import com.openapi.core.model.NullReturnVo
import com.openapi.core.config.Constants
import com.openapi.core.account.UserLoginUtil
import com.openapi.feature.user.login.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import com.openapi.common.ui.theme.AppMaterialTheme

@Composable
internal fun FeedCard(entry: IWannaVo, onLike: () -> Unit) {
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
internal fun CommentBar(
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
internal fun RechargePicker(
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
