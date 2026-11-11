package com.openapi.home.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.openapi.common.design.theme.AppColors
import com.openapi.ks.myapp.ui.localvideo.LocalVideoScreen
import com.openapi.ks.chat.R

/** 频道页：顶部两个 tab，「频道」是在线频道列表，「本地视频」是手机里的视频（按文件夹分组、播放、删除）。 */
@Composable
fun ChannelsHub() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = AppColors.ChatBackground,
            contentColor = AppColors.Primary,
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.home_tab_channels)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.localvideo_title)) })
        }
        Box(Modifier.fillMaxWidth().fillMaxSize()) {
            if (tab == 0) ChannelsScreen() else LocalVideoScreen(onBack = null)
        }
    }
}
