package com.openapi.ks.myapp.dh

import android.content.Context
import android.view.View
import com.openapi.ks.dh.Live2dRuntime
import com.openapi.ks.myapp.avatar.AvatarCapability
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.TouchResult

/**
 * Live2D 适配器：直接调 `:dh-engines` 的 [Live2dRuntime]（已内置 Java/JNI 包装，无反射）。
 * 素材没下好（LIVE2D 清单仍空，或 cache 里还没有模型）时 [availability] 回「未下载」并标
 * [DhEngineAvailability.needsDownload]；JNI 包装库缺失时回「native 库缺失」并 blocked。
 * 口型走 [Live2dRuntime.setMouth]，情绪走 [Live2dRuntime.setExpression]。
 */
class Live2dDigitalHumanEngine(
    private val modelDirName: String = Live2dRuntime.DEFAULT_MODEL_DIR,
    private val modelJsonName: String = "$modelDirName.model3.json",
) : DigitalHumanEngine {

    override val id: DhEngineId = DhEngineId.LIVE2D

    override val capabilities: Set<AvatarCapability> = setOf(
        AvatarCapability.MOUTH,
        AvatarCapability.EMOTION,
        AvatarCapability.STATE,
    )

    private var runtime: Live2dRuntime? = null
    private var viewCreated = false
    private var released = false

    override fun bind(spec: AvatarSpec) {
        // Lab 默认模型；后续可把 AvatarSpec.assetKey 映射到目录名。
    }

    override fun dispatch(command: AvatarCommand) {
        val rt = runtime?.takeIf { viewCreated } ?: return
        when (command) {
            is AvatarCommand.Mouth -> rt.setMouth(command.openness)
            is AvatarCommand.Emotion -> rt.setExpression(command.name)
            else -> Unit
        }
    }

    override fun onTouch(event: AvatarTouch): TouchResult = TouchResult.Ignored

    override fun release() {
        if (released) return
        released = true
        runtime?.release()
        runtime = null
        viewCreated = false
    }

    override fun createView(context: Context): View? {
        if (released) return null
        // Pass the Activity-bearing host context through — not applicationContext — so
        // Live2dRuntime can SetActivityInstance / build GLSurfaceView the same way the
        // pre-facade adapter did.
        val view = runtime(context).createView(context) ?: return null
        viewCreated = true
        return view
    }

    override fun availability(context: Context): DhEngineAvailability {
        val status = runtime(context).availability()
        return DhEngineAvailability(
            id = id,
            available = status.available,
            reason = status.reason,
            needsDownload = !status.available && status.reason == Live2dRuntime.REASON_NOT_DOWNLOADED,
        )
    }

    private fun runtime(context: Context): Live2dRuntime =
        runtime ?: Live2dRuntime(
            context,
            DhLabBootstrap.assetStore(context),
            modelDirName,
            modelJsonName,
        ).also { runtime = it }
}
