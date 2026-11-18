package com.openapi.ks.myapp.dh

import android.content.Context
import android.util.Log
import android.view.View
import com.openapi.ks.myapp.avatar.AvatarCapability
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.TouchResult

/**
 * TaoAvatar（MNN）适配器占位：旗舰门闸 + SDK 探测。
 * 完整 A2BS/NNR 接线依赖精简模块编入；未编入或机型不足时灰显。
 */
class TaoDigitalHumanEngine : DigitalHumanEngine {

    override val id: DhEngineId = DhEngineId.TAO_AVATAR

    override val capabilities: Set<AvatarCapability> = setOf(
        AvatarCapability.MOUTH,
        AvatarCapability.STATE,
    )

    private var released = false

    override fun bind(spec: AvatarSpec) {}

    override fun dispatch(command: AvatarCommand) {
        // 真机 SDK 就绪后再把 Mouth/PCM 接到 A2BS；现阶段静默忽略。
    }

    override fun onTouch(event: AvatarTouch): TouchResult = TouchResult.Ignored

    override fun release() {
        released = true
    }

    override fun createView(context: Context): View? {
        if (!TaoSdkReflect.isPresent()) return null
        return try {
            TaoSdkReflect.newAvatarTextureView(context)
        } catch (t: Throwable) {
            Log.e(TAG, "createView failed", t)
            null
        }
    }

    override fun availability(context: Context): DhEngineAvailability {
        val gate = TaoDeviceGate.evaluate(context)
        if (!gate.supported) {
            return DhEngineAvailability(id, available = false, reason = gate.reason)
        }
        if (!TaoSdkReflect.isPresent()) {
            return DhEngineAvailability(id, available = false, reason = "未编入")
        }
        return DhEngineAvailability(id, available = true)
    }

    companion object {
        private const val TAG = "TaoDhEngine"
    }
}

internal object TaoSdkReflect {
    /** MnnTaoAvatar 主视图类名；精简模块编入后需保持或改此常量。 */
    private const val AVATAR_TEXTURE =
        "com.taobao.meta.avatar.nnr.AvatarTextureView"

    fun isPresent(): Boolean = runCatching { Class.forName(AVATAR_TEXTURE) }.isSuccess

    fun newAvatarTextureView(context: Context): View {
        val cls = Class.forName(AVATAR_TEXTURE)
        val ctor = cls.constructors.firstOrNull { it.parameterTypes.size == 1 &&
            Context::class.java.isAssignableFrom(it.parameterTypes[0]) }
            ?: cls.getConstructor(Context::class.java)
        return ctor.newInstance(context) as View
    }
}
