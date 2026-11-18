package com.openapi.ks.myapp.dh

import android.content.Context
import android.view.View
import com.openapi.ks.myapp.avatar.AvatarCapability
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.SimpleAvatarRenderer
import com.openapi.ks.myapp.avatar.TouchResult

/**
 * 基线引擎：委托现有 [SimpleAvatarRenderer]，永远可用、零 native 依赖。
 * Lab 切换时的对照物，也是非 Lab 路径的行为等价物。
 */
class SimpleDigitalHumanEngine(
    private val inner: SimpleAvatarRenderer = SimpleAvatarRenderer(),
) : DigitalHumanEngine {

    override val id: DhEngineId = DhEngineId.SIMPLE

    /** 给 Compose [com.openapi.ks.myapp.avatar.SimpleAvatar] 用。 */
    val renderer: SimpleAvatarRenderer get() = inner

    override val capabilities: Set<AvatarCapability> get() = inner.capabilities

    override fun bind(spec: AvatarSpec) = inner.bind(spec)

    override fun dispatch(command: AvatarCommand) = inner.dispatch(command)

    override fun onTouch(event: AvatarTouch): TouchResult = inner.onTouch(event)

    override fun release() = inner.release()

    override fun createView(context: Context): View? = null

    override fun availability(context: Context) =
        DhEngineAvailability(DhEngineId.SIMPLE, available = true)
}

/** 注册 P0 默认引擎。幂等。 */
object DhLabBootstrap {
    @Volatile
    private var done = false

    fun ensureDefaults() {
        if (done) return
        synchronized(this) {
            if (done) return
            DhEngineRegistry.register(DhEngineId.SIMPLE) { SimpleDigitalHumanEngine() }
            done = true
        }
    }

    /** 单测在 [DhEngineRegistry.resetForTests] 之后要能重新注册。 */
    fun resetForTests() {
        done = false
    }
}
