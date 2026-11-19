package com.openapi.ks.myapp.dh

import android.content.Context
import android.view.View
import com.openapi.ks.dh.TaoRuntime
import com.openapi.ks.myapp.avatar.AvatarCapability
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.TouchResult

/**
 * TaoAvatar（MNN）适配器：直接调 `:dh-engines` 的 [TaoRuntime]（已内置 NNR 运行时，无反射）。
 * 顺序：旗舰门闸 [TaoDeviceGate] 优先（机型不足下完素材也不能用），然后素材未就绪回「未下载」并标
 * [DhEngineAvailability.needsDownload]，最后 JNI 库缺失回「native 库缺失」。
 * v1 只保证 NNR 网格出画面；口型/A2BS 尚未接线，[dispatch] 静默忽略。
 */
class TaoDigitalHumanEngine : DigitalHumanEngine {

    override val id: DhEngineId = DhEngineId.TAO_AVATAR

    // MOUTH 故意不声明：dispatch() 现在静默忽略 Mouth（A2BS 未接线），声明了就是让调用方
    // 相信一个没有效果的 capability（Final review I10）。接上 A2BS 后再加回来。
    override val capabilities: Set<AvatarCapability> = setOf(
        AvatarCapability.STATE,
    )

    private var runtime: TaoRuntime? = null
    private var released = false

    override fun bind(spec: AvatarSpec) {}

    override fun dispatch(command: AvatarCommand) {
        // 真机 SDK 就绪后再把 Mouth/PCM 接到 A2BS；现阶段静默忽略。
    }

    override fun onTouch(event: AvatarTouch): TouchResult = TouchResult.Ignored

    override fun release() {
        if (released) return
        released = true
        runtime?.release()
        runtime = null
    }

    override fun createView(context: Context): View? {
        if (released) return null
        // Pass the Activity-bearing host context through — the TextureView and its Choreographer
        // rendering loop attach to it.
        return runtime(context).createView(context)
    }

    override fun availability(context: Context): DhEngineAvailability {
        val gate = TaoDeviceGate.evaluate(context)
        if (!gate.supported) {
            return DhEngineAvailability(id, available = false, reason = gate.reason)
        }
        val status = runtime(context).availability()
        return DhEngineAvailability(
            id = id,
            available = status.available,
            reason = status.reason,
            needsDownload = !status.available && status.reason == TaoRuntime.REASON_NOT_DOWNLOADED,
        )
    }

    private fun runtime(context: Context): TaoRuntime =
        runtime ?: TaoRuntime(DhLabBootstrap.assetStore(context)).also { runtime = it }
}
