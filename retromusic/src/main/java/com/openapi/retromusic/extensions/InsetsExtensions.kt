package com.openapi.retromusic.extensions

import androidx.core.view.WindowInsetsCompat
import com.openapi.retromusic.util.PreferenceUtil
import com.openapi.retromusic.util.RetroUtil

fun WindowInsetsCompat?.getBottomInsets(): Int {
    return if (PreferenceUtil.isFullScreenMode) {
        return 0
    } else {
        this?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: RetroUtil.navigationBarHeight
    }
}
