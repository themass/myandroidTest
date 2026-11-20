package com.openapi.app.data.home

import android.app.Activity
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.openapi.feature.content.home.domain.ChannelEntries
import com.openapi.app.home.domain.HomeEntries
import com.openapi.feature.user.home.domain.ProfileEntries
import com.openapi.feature.chat.data.log.ConnLogUtil
import com.openapi.core.update.VersionUpdater
import com.openapi.feature.video.PlaybackEngineHelper

/**
 * 用旧的实现组合出 [HomeEntries]：频道部分和「我的」部分分别委托给两个实现类。
 */
class LegacyHomeEntries(app: Context) : HomeEntries,
    ChannelEntries by LegacyChannelEntries(app),
    ProfileEntries by LegacyProfileEntries(app) {

    @OptIn(UnstableApi::class)
    override fun onHomeStarted(context: Context) {
        (context as? Activity)?.let { VersionUpdater.checkUpdate(it, false) }
        ConnLogUtil.sendAllLog(context)
        PlaybackEngineHelper.applyEngine(context)
    }
}
