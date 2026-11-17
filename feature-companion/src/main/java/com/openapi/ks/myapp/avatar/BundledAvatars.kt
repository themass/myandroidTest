package com.openapi.ks.myapp.avatar

import android.content.Context
import java.io.File

/**
 * 随应用内置的示例形象（19 层 PNG，512×768）。
 *
 * 走和下载形象完全相同的安装路径（sha256 校验 → 解压 → manifest 校验 → 原子入库），
 * 所以它也是对安装流程的一次真实检验。哈希是固定的：资源被改动时必须同步更新。
 */
object BundledAvatars {
    const val DEMO_KEY = "demo-avatar-v1"
    private const val DEMO_VERSION = 1
    private const val DEMO_ASSET = "avatars/demo-avatar-v1.zip"
    private const val DEMO_SHA256 = "d7af7d7a5c756bd3c01b0061664059d67a699e2585af76d5447de8e1e807bd04"

    /** 确保示例形象已安装。已经装好则什么都不做；失败抛异常，由调用方决定是否忽略。 */
    fun ensureDemo(context: Context, store: AvatarPackStore) {
        if (store.installedVersion(DEMO_KEY) != null) return
        val zip = File(context.cacheDir, "avatar-demo-install.zip")
        try {
            context.assets.open(DEMO_ASSET).use { input -> zip.outputStream().use { input.copyTo(it) } }
            store.install(DEMO_KEY, DEMO_VERSION, DEMO_SHA256, zip)
        } finally {
            zip.delete()
        }
    }
}
