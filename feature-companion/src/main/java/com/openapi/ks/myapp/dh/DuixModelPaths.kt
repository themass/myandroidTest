package com.openapi.ks.myapp.dh

import android.content.Context
import com.openapi.ks.dh.DuixRuntime
import com.openapi.ks.dh.EngineAssets
import java.io.File

/**
 * Duix 模型落盘约定：`AssetStore.cacheRoot(DUIX)/model/{gj_dh_res,<modelName>}`，
 * 完成标记在同级 `model/tmp/<name>`（见 `AssetManifest` 的 DUIX 布局，与 SDK 的 DUIX.init 校验一致）。
 */
object DuixModelPaths {
    /** 官方 Oliver 角色包，解压后目录名即 SDK 的 modelName。 */
    const val DEFAULT_MODEL = DuixRuntime.DEFAULT_MODEL
    const val BASE_DIR = DuixRuntime.BASE_MODEL

    data class Status(val ready: Boolean, val reason: String, val modelName: String)

    fun duixRoot(context: Context): File =
        DhLabBootstrap.assetStore(context).cacheRoot(EngineAssets.DUIX)

    fun status(context: Context, modelName: String = DEFAULT_MODEL): Status {
        val root = duixRoot(context)
        val missing = listOf(BASE_DIR, modelName).filterNot { name ->
            File(root, "model/$name").exists() && File(root, "model/tmp/$name").exists()
        }
        return if (missing.isEmpty()) {
            Status(ready = true, reason = "", modelName = modelName)
        } else {
            Status(ready = false, reason = DuixRuntime.REASON_NOT_DOWNLOADED, modelName = modelName)
        }
    }
}
