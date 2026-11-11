package com.openapi.feature.user.home.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.openapi.common.ui.theme.AppColors
import com.openapi.feature.user.home.ProfileDependencies
import com.openapi.feature.user.home.domain.ProfileAction
import com.openapi.feature.user.home.domain.ProfileEntry
import com.openapi.feature.user.home.domain.ProfileInfo
import com.openapi.core.update.VersionUpdater

/**
 * 「我的」页（A22）：暖色背景；顶部个人卡（未登录时点击去登录）；快捷入口；设置与关于。
 * 用量统计没有数据来源，这一版不显示。
 */
@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entries = remember { ProfileDependencies.require() }
    var info by remember { mutableStateOf(entries.profile(context)) }
    // 从登录页、设置页回来时刷新登录态
    LifecycleResumeEffect(Unit) {
        info = entries.profile(context)
        onPauseOrDispose { }
    }

    val quick = info.entries.filter {
        it.action == ProfileAction.PhotoSolve || it.action == ProfileAction.Share ||
            it.action == ProfileAction.AppRecommend || it.action == ProfileAction.Donation
    }
    val rest = info.entries.filter { e -> quick.none { it.action == e.action } && e.action != ProfileAction.Login }

    Column(
        modifier
            .fillMaxSize()
            .background(AppColors.ChatBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileCard(info) { entries.perform(context, ProfileAction.Login) }

        if (quick.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.Surface).padding(12.dp),
            ) {
                Text(stringResource(com.openapi.feature.user.R.string.ui_shortcuts), color = AppColors.TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.size(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    quick.forEach { entry ->
                        QuickTile(entry) {
                            entries.perform(context, entry.action)
                            info = entries.profile(context)
                        }
                    }
                    LocalVideoTile {
                        // 本地视频在 :feature-video，这里只用类名打开，不依赖该模块
                        context.startActivity(
                            android.content.Intent().setClassName(context, "com.openapi.feature.video.local.LocalVideoActivity"),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.Surface),
        ) {
            rest.forEachIndexed { index, entry ->
                ProfileRow(entry) {
                    entries.perform(context, entry.action)
                    info = entries.profile(context)
                }
                if (index < rest.lastIndex) {
                    androidx.compose.material3.HorizontalDivider(color = AppColors.Divider)
                }
            }
            androidx.compose.material3.HorizontalDivider(color = AppColors.Divider)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(com.openapi.feature.user.R.string.menu_btn_about), color = AppColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(VersionUpdater.getVersion(), color = AppColors.TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun ProfileCard(info: ProfileInfo, onLoginClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .clickable(enabled = !info.loggedIn, onClick = onLoginClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(info.avatarRes), contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.name, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (info.levelRes != 0) {
                    Spacer(Modifier.width(6.dp))
                    Image(painterResource(info.levelRes), contentDescription = null)
                }
            }
            Spacer(Modifier.size(2.dp))
            Text(info.hint, color = AppColors.TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun QuickTile(entry: ProfileEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(entry.iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(4.dp))
        Text(stringResource(entry.titleRes), color = AppColors.TextPrimary, fontSize = 12.sp)
    }
}

@Composable
internal fun LocalVideoTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(com.openapi.common.R.drawable.ic_ctype_video), contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(4.dp))
        Text(stringResource(com.openapi.feature.user.R.string.localvideo_title), color = AppColors.TextPrimary, fontSize = 12.sp)
    }
}

@Composable
internal fun ProfileRow(entry: ProfileEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(entry.iconRes), contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(stringResource(entry.titleRes), color = AppColors.TextPrimary, fontSize = 15.sp)
    }
}

@Composable
fun ProfileHeader(info: ProfileInfo, onLoginClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !info.loggedIn, onClick = onLoginClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(info.avatarRes), contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.width(8.dp))
        if (info.levelRes != 0) {
            Image(painterResource(info.levelRes), contentDescription = null)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            info.name,
            color = AppColors.Primary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Text(info.hint, color = AppColors.TextSecondary, fontSize = 14.sp)
    }
}
