package com.openapi.ks.myapp.dh

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.View
import com.openapi.ks.myapp.avatar.AvatarCapability
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.TouchResult

/**
 * ChatWaifu Live2D 适配器：反射 `JniBridgeJava` / `GLRenderer`。
 * 不接 VITS/Sherpa；口型走 `nativeProjectMouthOpenY`，情绪走 `nativeApplyExpression`。
 */
class Live2dDigitalHumanEngine(
    private val modelDirName: String = DEFAULT_MODEL_DIR,
    private val modelJsonName: String = "$DEFAULT_MODEL_DIR.model3.json",
) : DigitalHumanEngine {

    override val id: DhEngineId = DhEngineId.LIVE2D

    override val capabilities: Set<AvatarCapability> = setOf(
        AvatarCapability.MOUTH,
        AvatarCapability.EMOTION,
        AvatarCapability.STATE,
    )

    private var surface: GLSurfaceView? = null
    private var started = false
    private var released = false

    override fun bind(spec: AvatarSpec) {
        // Lab 默认模型；后续可把 AvatarSpec.assetKey 映射到目录名。
    }

    override fun dispatch(command: AvatarCommand) {
        if (!Live2dSdkReflect.isPresent()) return
        when (command) {
            is AvatarCommand.Mouth -> {
                val open = command.openness.coerceIn(0f, 1f)
                Live2dSdkReflect.mouthOpenY(open)
            }
            is AvatarCommand.Emotion -> {
                Live2dSdkReflect.applyExpression(command.name)
            }
            else -> Unit
        }
    }

    override fun onTouch(event: AvatarTouch): TouchResult = TouchResult.Ignored

    override fun release() {
        if (released) return
        released = true
        runCatching {
            if (started) {
                Live2dSdkReflect.nativeOnStop()
                Live2dSdkReflect.nativeOnDestroy()
            }
        }
        surface = null
        started = false
    }

    override fun createView(context: Context): View? {
        if (!Live2dSdkReflect.isPresent()) return null
        return try {
            val activity = context.findActivity()
            if (activity != null) {
                Live2dSdkReflect.setActivityInstance(activity)
            }
            Live2dSdkReflect.setContext(context.applicationContext)
            val view = GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setRenderer(Live2dSdkReflect.newGlRenderer())
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
            Live2dSdkReflect.nativeOnStart()
            Live2dSdkReflect.projectChangeTo(
                // ChatWaifu 约定：目录名 + model3 文件名
                modelPath = modelDirName,
                modelJsonFileName = modelJsonName,
            )
            Live2dSdkReflect.autoBlink(true)
            started = true
            surface = view
            view
        } catch (t: Throwable) {
            Log.e(TAG, "createView failed", t)
            null
        }
    }

    override fun availability(context: Context): DhEngineAvailability {
        if (!Live2dSdkReflect.isPresent()) {
            return DhEngineAvailability(id, available = false, reason = "未编入")
        }
        // 模型在 Live2D 模块 assets；编入即视为可试跑（缺具体角色时 native 会回调错误，UI 仍可切）
        return DhEngineAvailability(id, available = true)
    }

    companion object {
        private const val TAG = "Live2dDhEngine"
        /** ChatWaifu 自带样例之一。 */
        const val DEFAULT_MODEL_DIR = "ATRI"
    }
}

internal object Live2dSdkReflect {
    private const val JNI = "com.chatwaifu.live2d.JniBridgeJava"
    private const val GL = "com.chatwaifu.live2d.GLRenderer"

    fun isPresent(): Boolean = runCatching { Class.forName(JNI) }.isSuccess

    private fun jni(): Class<*> = Class.forName(JNI)

    fun setContext(context: Context) {
        jni().getMethod("SetContext", Context::class.java).invoke(null, context)
    }

    fun setActivityInstance(activity: Activity) {
        jni().getMethod("SetActivityInstance", Activity::class.java).invoke(null, activity)
    }

    fun newGlRenderer(): GLSurfaceView.Renderer {
        return Class.forName(GL).getDeclaredConstructor().newInstance() as GLSurfaceView.Renderer
    }

    fun nativeOnStart() = jni().getMethod("nativeOnStart").invoke(null)
    fun nativeOnStop() = jni().getMethod("nativeOnStop").invoke(null)
    fun nativeOnDestroy() = jni().getMethod("nativeOnDestroy").invoke(null)

    fun projectChangeTo(modelPath: String, modelJsonFileName: String) {
        jni().getMethod("nativeProjectChangeTo", String::class.java, String::class.java)
            .invoke(null, modelPath, modelJsonFileName)
    }

    fun mouthOpenY(value: Float) {
        jni().getMethod("nativeProjectMouthOpenY", Float::class.javaPrimitiveType)
            .invoke(null, value)
    }

    fun applyExpression(name: String) {
        jni().getMethod("nativeApplyExpression", String::class.java).invoke(null, name)
    }

    fun autoBlink(enabled: Boolean) {
        jni().getMethod("nativeAutoBlinkEyes", Boolean::class.javaPrimitiveType)
            .invoke(null, enabled)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
