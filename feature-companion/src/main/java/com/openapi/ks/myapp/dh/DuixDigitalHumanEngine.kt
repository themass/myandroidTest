package com.openapi.ks.myapp.dh

import android.content.Context
import android.view.View
import com.openapi.ks.dh.DuixRuntime
import com.openapi.ks.myapp.avatar.AvatarCapability
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.TouchResult

/**
 * Duix 适配器：直接调 `:dh-engines` 的 [DuixRuntime]（已内置 Duix 运行时，无反射）。
 * 素材没下好时 [availability] 回「未下载」并标 [DhEngineAvailability.needsDownload]，菜单据此走下载闸门；
 * 就绪后 PCM 只驱动口型（`setVolume(0)`），扬声器仍走 LiveKit。
 */
class DuixDigitalHumanEngine(
    private val modelName: String = DuixModelPaths.DEFAULT_MODEL,
) : DigitalHumanEngine {

    override val id: DhEngineId = DhEngineId.DUIX

    override val capabilities: Set<AvatarCapability> = setOf(
        AvatarCapability.MOUTH,
        AvatarCapability.GESTURE,
        AvatarCapability.STATE,
    )

    /** Duix 口型只认 pushPcm；测试语音 rig 据此直推 PCM，不发 Mouth（会合成假 PCM）。 */
    override val pcmDriven: Boolean = true

    private var runtime: DuixRuntime? = null
    private var viewCreated = false
    private var released = false

    override fun bind(spec: AvatarSpec) {
        // Duix 模型由 [DuixModelPaths] 固定；AvatarSpec 暂不映射到 modelName。
    }

    override fun dispatch(command: AvatarCommand) {
        val rt = runtime?.takeIf { viewCreated } ?: return
        when (command) {
            // SDK 的动作区间按序号命名；非数字 id 暂不支持。
            is AvatarCommand.Gesture -> command.id.toIntOrNull()?.let { rt.startMotion(it, true) }
            is AvatarCommand.Mouth -> {
                // 通话页已有 mouthLevel；在接上 LiveKit PCM 抽流之前，用幅度合成短 PCM 驱动口型。
                val amp = command.openness.coerceIn(0f, 1f)
                if (amp >= 0.02f) {
                    pushRemotePcm(synthesizePcm16k(amp))
                }
            }
            is AvatarCommand.SetState,
            is AvatarCommand.Emotion,
            // Duix 没有表情图通道，「收回立绘」对它是天然满足的空操作。
            is AvatarCommand.ClearEmotion,
            is AvatarCommand.Gaze -> Unit
        }
    }

    /** ~20ms @ 16kHz mono PCM16，幅度随 openness；仅 Lab 过渡用。 */
    private fun synthesizePcm16k(openness: Float): ByteArray {
        val samples = 320
        val out = ByteArray(samples * 2)
        val amp = (openness * 2000).toInt().coerceIn(0, 8000)
        var i = 0
        while (i < samples) {
            val v = (kotlin.math.sin(i * 0.35) * amp).toInt().toShort()
            out[i * 2] = (v.toInt() and 0xff).toByte()
            out[i * 2 + 1] = (v.toInt() shr 8).toByte()
            i++
        }
        return out
    }

    override fun onTouch(event: AvatarTouch): TouchResult = TouchResult.Ignored

    override fun pushRemotePcm(pcm16leMono16k: ByteArray) {
        val rt = runtime?.takeIf { viewCreated } ?: return
        rt.pushPcm(pcm16leMono16k)
    }

    override fun release() {
        if (released) return
        released = true
        runtime?.release()
        runtime = null
        viewCreated = false
    }

    override fun createView(context: Context): View? {
        if (released) return null
        val rt = runtime(context)
        val view = rt.createView() ?: return null
        // 只驱动口型，LiveKit 出声（设计 §9）
        rt.setVolume(0f)
        viewCreated = true
        return view
    }

    override fun availability(context: Context): DhEngineAvailability {
        val status = runtime(context).availability()
        return DhEngineAvailability(
            id = id,
            available = status.available,
            reason = status.reason,
            needsDownload = !status.available && status.reason == DuixRuntime.REASON_NOT_DOWNLOADED,
        )
    }

    private fun runtime(context: Context): DuixRuntime =
        runtime ?: DuixRuntime(context, DhLabBootstrap.assetStore(context), modelName)
            .also { runtime = it }
}
