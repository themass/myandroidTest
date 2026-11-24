package com.openapi.ks.dh

/**
 * 进程级 native Live2D 场景的归属登记（代次守卫）。
 *
 * `libchatwaifu-live2d.so` 的 LAppDelegate / LAppLive2DManager 是进程全局单例：native
 * 场景同一时刻只有一份，堆上却可以活着多个 [Live2dRuntime]（引擎切换、debug 页与通话页
 * 并存）。真机 ADY-AL00 2026-11-25 两类 SIGSEGV 的共同根因就是归属不清：
 * - 旧主人 release 时对**共享**单例跑 nativeOnStop/OnDestroy，拆掉新主人正在渲染的场景；
 * - 新实例 OnStart 换掉正被旧 GLThread 渲染的 LAppView（fork v2 已在 C++ 侧改成幂等复用）。
 *
 * 规则：后来者 claim 接管并拿到被逐出的旧主人；旧主人的 release 不得清空当前归属，
 * 因而也不得再跑 native 拆除（拆除只属于当前主人）。线程约定：claim/release 都在
 * 主线程（buildGlView / release 的调用线程），GL 线程只读不写。
 */
internal class Live2dNativeOwnership {
    /**
     * 可被逐出的宿主：交还归属权时**必须当场停止**继续喂 native 单例的渲染源
     * （GLSurfaceView/GLThread）。只让旧实例「不拆 native」不够——它的 GL 线程还活着，
     * 每帧 nativeOnDrawFrame 与新人格互踩同一个进程级单例（两个 EGL 上下文）。
     */
    interface Evictable {
        fun onEvictedFromNativeScene()
    }

    private var owner: Any? = null

    /** 登记 [owner] 为 native 场景主人；返回被逐出的前任（无前任返回 null）。 */
    fun claim(owner: Any): Any? {
        val previous = this.owner
        this.owner = owner
        if (previous != null && previous !== owner) {
            (previous as? Evictable)?.onEvictedFromNativeScene()
        }
        return previous
    }

    /** [owner] 是否是当前主人（身份比较，运行时实例无 value-equality）。 */
    fun isOwner(owner: Any): Boolean = this.owner === owner

    /** 主人 release 返回 true 并清空归属；非主人（被逐出的旧实例）返回 false，归属不动。 */
    fun release(owner: Any): Boolean {
        if (this.owner !== owner) return false
        this.owner = null
        return true
    }
}
