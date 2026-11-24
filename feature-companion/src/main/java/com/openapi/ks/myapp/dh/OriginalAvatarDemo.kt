package com.openapi.ks.myapp.dh

import android.content.Context
import android.content.pm.ApplicationInfo
import com.openapi.ks.dh.AssetStore
import com.openapi.ks.dh.EngineAssets
import java.io.File
import java.io.IOException

/** Installs the synthetic Live2D sample from debug-only APK assets into the normal runtime cache. */
object OriginalAvatarDemo {
    const val MODEL_DIR = "OriginalDemo"
    private const val ASSET_ROOT = "live2d/original-demo"
    private const val ASSET_VERSION = "1"
    private const val VERSION_FILE = ".asset-version"
    private val files = listOf(
        "OriginalDemo.model3.json",
        "OriginalDemo.moc3",
        "OriginalDemo.cdi3.json",
        "texture_00.png",
        "demo_default.exp3.json",
        "demo_smile.exp3.json",
        "demo_cape.exp3.json",
        "demo_smile_cape.exp3.json",
    )

    fun expressionName(smile: Boolean, cape: Boolean): String = when {
        smile && cape -> "DemoSmileCape"
        smile -> "DemoSmile"
        cape -> "DemoCape"
        else -> "DemoDefault"
    }

    /** Copies only in debuggable builds; release APKs do not contain these model assets. */
    @Synchronized
    fun install(context: Context): Result<File> = runCatching {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            throw IOException("Original Live2D demo is available in debug builds only")
        }

        val appContext = context.applicationContext
        val root = AssetStore(appContext).cacheRoot(EngineAssets.LIVE2D)
        val target = File(root, MODEL_DIR)
        val version = File(target, VERSION_FILE)
        if (version.isFile && version.readText() == ASSET_VERSION && files.all { File(target, it).isFile }) {
            return@runCatching target
        }

        if (!root.exists() && !root.mkdirs()) throw IOException("Cannot create Live2D cache")
        val staging = File(root, ".$MODEL_DIR.installing")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("Cannot create Live2D demo staging directory")
        try {
            files.forEach { name ->
                appContext.assets.open("$ASSET_ROOT/$name").use { input ->
                    File(staging, name).outputStream().use { output -> input.copyTo(output) }
                }
            }
            File(staging, VERSION_FILE).writeText(ASSET_VERSION)
            if (target.exists() && !target.deleteRecursively()) {
                throw IOException("Cannot replace existing Live2D demo")
            }
            if (!staging.renameTo(target)) throw IOException("Cannot install Live2D demo")
            target
        } finally {
            staging.deleteRecursively()
        }
    }
}
