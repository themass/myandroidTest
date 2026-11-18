package com.openapi.ks.myapp.dh

import android.content.Context
import java.io.File

/**
 * Duix 模型落盘约定（与官方 SDK [ai.guiji.duix.sdk.client.DUIX] 一致）：
 * `context.getExternalFilesDir("duix")/model/{gj_dh_res,<modelName>}`
 * 以及同名 tag 目录 `model/tmp/...`。
 */
object DuixModelPaths {
    const val DEFAULT_MODEL = "duix_lab"

    data class Status(val ready: Boolean, val reason: String, val modelName: String)

    fun duixRoot(context: Context): File =
        context.getExternalFilesDir("duix") ?: File(context.filesDir, "duix")

    fun status(context: Context, modelName: String = DEFAULT_MODEL): Status {
        val root = duixRoot(context)
        val base = File(root, "model/gj_dh_res")
        val baseTag = File(root, "model/tmp/gj_dh_res")
        if (!base.exists() || !baseTag.exists()) {
            return Status(
                ready = false,
                reason = "缺模型 gj_dh_res（见 docs/dh-lab-models.md）",
                modelName = modelName,
            )
        }
        val modelDir = File(root, "model/$modelName")
        val modelTag = File(root, "model/tmp/$modelName")
        if (!modelDir.exists() || !modelTag.exists()) {
            return Status(
                ready = false,
                reason = "缺模型 $modelName",
                modelName = modelName,
            )
        }
        return Status(ready = true, reason = "", modelName = modelName)
    }
}
