package com.openapi.retromusic.util

import android.content.Context
import androidx.core.content.FileProvider

// 宿主 app 自己声明了一个 androidx.core.content.FileProvider，同类名的 <provider> 在清单合并时会被当成
// 同一个元素而冲突；这里用独立子类和独立 authority 让两者共存。
class RetroFileProvider : FileProvider() {
    companion object {
        fun authority(context: Context): String = context.applicationContext.packageName + ".retromusic.fileprovider"
    }
}
