package com.openapi.ks.myapp.dh

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.openapi.ks.myapp.avatar.AvatarCapability
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.TouchResult
import java.lang.reflect.Proxy

/**
 * Duix 适配器：通过反射挂官方 `DUIX` + `DUIXTextureView`，避免 companion 强依赖 native SDK。
 * 未编入 / 缺模型时 [availability] 灰显；就绪后 PCM 只驱动口型（`setVolume(0)`），扬声器仍走 LiveKit。
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

    private var host: FrameLayout? = null
    private var duix: Any? = null
    private var pushing = false
    private var released = false

    override fun bind(spec: AvatarSpec) {
        // Duix 模型路径由 [DuixModelPaths] 固定；AvatarSpec 暂不映射到 modelName。
    }

    override fun dispatch(command: AvatarCommand) {
        val sdk = duix ?: return
        when (command) {
            is AvatarCommand.Gesture -> invoke(sdk, "startMotion", command.id, true)
            is AvatarCommand.Mouth -> {
                // 通话页已有 mouthLevel；在接上 LiveKit PCM 抽流之前，用幅度合成短 PCM 驱动口型。
                val amp = command.openness.coerceIn(0f, 1f)
                if (amp >= 0.02f) {
                    pushRemotePcm(synthesizePcm16k(amp))
                }
            }
            is AvatarCommand.SetState,
            is AvatarCommand.Emotion,
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
        val sdk = duix ?: return
        if (!pushing) {
            invoke(sdk, "startPush")
            pushing = true
        }
        invoke(sdk, "pushPcm", pcm16leMono16k)
    }

    override fun release() {
        if (released) return
        released = true
        val sdk = duix
        duix = null
        host = null
        if (sdk != null) {
            runCatching {
                if (pushing) invoke(sdk, "stopPush")
                invoke(sdk, "release")
            }
        }
        pushing = false
    }

    override fun createView(context: Context): View? {
        if (!DuixSdkReflect.isPresent()) return null
        val status = DuixModelPaths.status(context, modelName)
        if (!status.ready) return null

        val frame = FrameLayout(context).also {
            it.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        host = frame

        return try {
            val textureView = DuixSdkReflect.newTextureView(context)
            frame.addView(
                textureView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            val renderer = DuixSdkReflect.newRenderer(context, textureView)
            DuixSdkReflect.configureTextureView(textureView, renderer)
            val callback = DuixSdkReflect.newCallback { event, msg, _ ->
                Log.i(TAG, "duix event=$event msg=$msg")
            }
            val instance = DuixSdkReflect.newDuix(context, modelName, renderer, callback)
            // 只驱动口型，LiveKit 出声（设计 §9）
            invoke(instance, "setVolume", 0.0f)
            invoke(instance, "init")
            duix = instance
            frame
        } catch (t: Throwable) {
            Log.e(TAG, "createView failed", t)
            null
        }
    }

    override fun availability(context: Context): DhEngineAvailability {
        if (!DuixSdkReflect.isPresent()) {
            return DhEngineAvailability(id, available = false, reason = "未编入")
        }
        val status = DuixModelPaths.status(context, modelName)
        if (!status.ready) {
            return DhEngineAvailability(id, available = false, reason = status.reason)
        }
        return DhEngineAvailability(id, available = true)
    }

    private fun invoke(target: Any, method: String, vararg args: Any?) {
        val types = args.map { arg ->
            when (arg) {
                null -> Any::class.java
                is Boolean -> Boolean::class.javaPrimitiveType!!
                is Float -> Float::class.javaPrimitiveType!!
                is ByteArray -> ByteArray::class.java
                else -> arg.javaClass
            }
        }.toTypedArray()
        val m = target.javaClass.methods.firstOrNull { it.name == method && it.parameterTypes.size == types.size }
            ?: return
        m.invoke(target, *args)
    }

    companion object {
        private const val TAG = "DuixDhEngine"
    }
}

/** 反射壳：类不在 classpath 时 [isPresent] = false。 */
internal object DuixSdkReflect {
    private const val DUIX = "ai.guiji.duix.sdk.client.DUIX"
    private const val TEXTURE = "ai.guiji.duix.sdk.client.render.DUIXTextureView"
    private const val RENDERER = "ai.guiji.duix.sdk.client.render.DUIXRenderer"
    private const val CALLBACK = "ai.guiji.duix.sdk.client.Callback"

    fun isPresent(): Boolean = runCatching { Class.forName(DUIX) }.isSuccess

    fun newTextureView(context: Context): View {
        val cls = Class.forName(TEXTURE)
        return cls.getConstructor(Context::class.java).newInstance(context) as View
    }

    fun newRenderer(context: Context, textureView: View): Any {
        val cls = Class.forName(RENDERER)
        return cls.getConstructor(Context::class.java, Class.forName(TEXTURE))
            .newInstance(context, textureView)
    }

    fun configureTextureView(textureView: View, renderer: Any) {
        // 对齐官方 CallActivity：EGL2 / 透明 / WHEN_DIRTY
        textureView.javaClass.getMethod("setEGLContextClientVersion", Int::class.javaPrimitiveType)
            .invoke(textureView, 2)
        runCatching {
            textureView.javaClass.getMethod(
                "setEGLConfigChooser",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).invoke(textureView, 8, 8, 8, 8, 16, 0)
        }
        textureView.javaClass.getMethod("setOpaque", Boolean::class.javaPrimitiveType)
            .invoke(textureView, false)
        val rendererIface = textureView.javaClass.methods
            .first { it.name == "setRenderer" }.parameterTypes[0]
        textureView.javaClass.getMethod("setRenderer", rendererIface).invoke(textureView, renderer)
        // RENDERMODE_WHEN_DIRTY = 0
        textureView.javaClass.getMethod("setRenderMode", Int::class.javaPrimitiveType)
            .invoke(textureView, 0)
    }

    fun newCallback(handler: (event: String, msg: String, info: Any?) -> Unit): Any {
        val iface = Class.forName(CALLBACK)
        return Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { _, method, args ->
            if (method.name == "onEvent" && args != null && args.size >= 2) {
                handler(
                    args[0] as? String ?: "",
                    args[1] as? String ?: "",
                    args.getOrNull(2),
                )
            }
            null
        }
    }

    fun newDuix(context: Context, modelName: String, renderSink: Any, callback: Any): Any {
        val cls = Class.forName(DUIX)
        val sinkIface = Class.forName("ai.guiji.duix.sdk.client.render.RenderSink")
        val cbIface = Class.forName(CALLBACK)
        return cls.getConstructor(
            Context::class.java,
            String::class.java,
            sinkIface,
            cbIface,
        ).newInstance(context, modelName, renderSink, callback)
    }
}
