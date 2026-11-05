/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package com.openapi.retromusic

import android.app.Application
import androidx.preference.PreferenceManager
import cat.ereza.customactivityoncrash.config.CaocConfig
import com.openapi.retromusic.appthemehelper.ThemeStore
import com.openapi.retromusic.appthemehelper.util.VersionUtils
import com.openapi.retromusic.activities.ErrorActivity
import com.openapi.retromusic.activities.MainActivity
import com.openapi.retromusic.appshortcuts.DynamicShortcutManager
import com.openapi.retromusic.glide.RetroMusicGlide
import com.openapi.retromusic.helper.WallpaperAccentManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * 原本是 Application 子类（`App : Application()`），改成普通 object 并提供 [init]。
 *
 * 这个模块是 vendor 进来的完整 app（Retro Music），不是从头设计的 feature 模块——它有
 * 自己的 Application 子类做 Koin 启动/主题默认值/崩溃处理器注册。但宿主 app 已经有
 * 自己的 `MyApplication`，一个进程只能有一个 Application 类，所以把原来 `onCreate()`
 * 里的逻辑原样搬进 [init]，由 `MyApplication.onCreate()` 调用一次，而不是让这个模块
 * 自己注册 Application——这样 Retro Music 内部大量 `App.getContext()` 调用点
 * （10 处）完全不用改。
 */
object App {

    private var instance: Application? = null
    private var wallpaperAccentManager: WallpaperAccentManager? = null

    @JvmStatic
    fun init(application: Application) {
        instance = application
        val manager = WallpaperAccentManager(application)
        wallpaperAccentManager = manager

        RetroMusicGlide.register(application)

        startKoin {
            androidContext(application)
            modules(appModules)
        }
        // default theme
        if (!ThemeStore.isConfigured(application, 3)) {
            ThemeStore.editTheme(application)
                .accentColorRes(com.openapi.retromusic.R.color.md_deep_purple_A200)
                .coloredNavigationBar(true)
                .commit()
        }
        manager.init()

        if (VersionUtils.hasNougatMR())
            DynamicShortcutManager(application).initDynamicShortcuts()

        // setting Error activity
        CaocConfig.Builder.create().errorActivity(ErrorActivity::class.java)
            .restartActivity(MainActivity::class.java).apply()

        // Set Default values for now playing preferences
        // This will reduce startup time for now playing settings fragment as Preference listener of AbsSlidingMusicPanelActivity won't be called
        PreferenceManager.setDefaultValues(application, R.xml.pref_now_playing_screen, false)
    }

    /** 宿主 app 退出时调用；原来挂在 Application.onTerminate()，这个回调在真机上几乎不会触发，保留只是不丢行为。 */
    @JvmStatic
    fun onTerminate() {
        wallpaperAccentManager?.release()
    }

    @JvmStatic
    fun getContext(): Application = instance!!

    @JvmStatic
    fun isProVersion(): Boolean = true
}
