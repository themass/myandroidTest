package com.openapi.ks.myapp.dh

import android.content.Context
import android.graphics.Color
import android.view.View
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.Rive
import com.openapi.ks.myapp.avatar.AvatarCapability
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarSpec

/**
 * Rive 官方 Android 运行时进通话舞台（lab only，**debug 变体**）。
 *
 * 与 Effects Lab 的 [com.openapi.app.fxlab] 同一套纪律：本 app 的 androidx.startup
 * 自动初始化被 tools:node="remove" 摘掉（APK 合并 manifest），构造任何 SDK 对象前
 * 必须 `Rive.init` 一次，否则 JNI 报 "No implementation found for
 * FileAssetLoader.constructor()"（真机踩过）。native 起不来就回 null——给不出可渲染
 * 面就不给 View，绝不抛（Vrm/Spine/TalkingHead 同语义）。
 *
 * 样本 .riv 按许可结论留在 :app 的 debug res（app/src/debug/raw-notes/README-fxlab-rive.md
 * 「不得挪出 debug」），本引擎按资源名运行时查找——release 里既无运行时也无样本，
 * availability 报「缺样本」只是兜底，真正的闸门是 release 不注册本引擎。
 */
class RiveDigitalHumanEngine : DigitalHumanEngine {

    override val id: DhEngineId = DhEngineId.RIVE

    /** 官方 loopy/circle_move 只有循环动画：没有情绪/口型/注视通道，capabilities 诚实为空。 */
    override val capabilities: Set<AvatarCapability> = emptySet()

    private var view: RiveAnimationView? = null
    private var released = false

    override fun bind(spec: AvatarSpec) = Unit

    override fun dispatch(command: AvatarCommand) = Unit

    override fun release() {
        if (released) return
        released = true
        val v = view ?: return
        view = null
        // 与 fxlab 的 onRelease 一致：stop 后脱离窗口树再 destroy 交 GC。
        runCatching { v.stop() }
        (v.parent as? android.view.ViewGroup)?.removeView(v)
    }

    override fun availability(context: Context): DhEngineAvailability {
        val ready = fixtureBytes(context) != null
        return DhEngineAvailability(
            id = id,
            available = ready,
            reason = if (ready) null else "缺样本（Rive .riv 只在 debug 资源里）",
        )
    }

    override fun createView(context: Context): View? {
        if (released) return null
        val bytes = fixtureBytes(context) ?: return null
        val animation = runCatching {
            riveGate.ensure { Rive.init(context.applicationContext) }
            riveGate.failure?.let { error(it) }
            RiveAnimationView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isOpaque = false
                // 装饰层语义与 fxlab 一致：不声明触控通道（舞台手势归引擎自己）。
                setOnTouchListener { _, _ -> false }
                setRiveBytes(bytes, autoplay = true, fit = Fit.CONTAIN, loop = Loop.LOOP)
            }
        }.onFailure { riveGate.failure }
            .getOrNull() ?: return null
        view = animation
        return animation
    }

    private fun fixtureBytes(context: Context): ByteArray? {
        val resId = FIXTURES.firstNotNullOfOrNull { name ->
            context.resources.getIdentifier(name, "raw", context.packageName).takeIf { it != 0 }
        } ?: return null
        return runCatching { context.resources.openRawResource(resId).use { it.readBytes() } }.getOrNull()
    }

    private companion object {
        /** 与 Effects Lab 共用 RiveRuntimeGate 的同款「失败只试一次」语义。 */
        val riveGate = RiveGate()

        @Volatile var failure: String? = null

        private val FIXTURES = listOf("fxlab_loopy", "fxlab_circle_move")
    }
}

/** 复制 fxlab 的 gate（模块间不依赖 :app；fxlab 那个在 app 包里）。 */
private class RiveGate {
    @Volatile private var done = false
    @Volatile var failure: String? = null
        private set

    fun ensure(init: () -> Unit) {
        if (done) return
        synchronized(this) {
            if (done) return
            runCatching(init).onFailure { failure = it.message ?: it.javaClass.simpleName }
            done = true // 失败也只试一次，失败原因由调用方展示
        }
    }
}
