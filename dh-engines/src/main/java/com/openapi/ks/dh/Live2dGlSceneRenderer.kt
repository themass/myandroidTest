package com.openapi.ks.dh

/**
 * GLSurfaceView 渲染回调的场景上装决策层（纯 JVM——native/GL 全部注入，单测钉死）。
 *
 * 修掉真机潜在白屏（2026-11-28 P0-A 排查取证）：旧实现里 `modelLoadedOnGl` 闩锁让
 * [prepareLive2dScene] 只在**首次** onSurfaceCreated 跑一次。GLSurfaceView 默认不保留
 * EGL context——onPause/onResume（后台回前台）、surface 重建之后，Cubism 模型手里的
 * 纹理/着色器句柄全部失效且没有重载路径：舞台白/黑且永不自愈。
 *
 * 规则：
 * 1. **每次** onSurfaceCreated 都重新上装「当前」模型（fork .so 的 OnStart 幂等复用
 *    _view，ChangeModelTo 先载后拆——Kotlin 侧重复 prepare 是安全的，见
 *    scripts/dh-lab/live2d-native-fork.patch）。
 * 2. 上装的是当前模型不是初始模型：热切换（changeModel）之后 EGL 重建不能把形象
 *    切回 ATRI。[prepareAndTrack] 只在成功时记录——native 先载后拆，失败时屏幕上
 *    还是旧模型，跟踪不能跟着脏。
 * 3. 被 [Live2dNativeOwnership] 逐出的旧宿主不得重建进程级共享场景（ownerQuery=false
 *    时只透传 native surface 事件）。
 * 4. 重建上装后回调 [onSceneReloaded]：GL 重建会清掉 native 触摸捕获等一次性状态，
 *    宿主据此复位（gazeCaptured=false，下一次注视重新 capture）。
 *
 * 全部方法只在 GL 线程调用（GLSurfaceView 渲染回调线程），无锁。
 */
internal class Live2dGlSceneRenderer(
    private val onSurfaceCreatedNative: () -> Unit,
    private val onSurfaceChangedNative: (Int, Int) -> Unit,
    private val onDrawFrameNative: () -> Unit,
    private val prepareScene: (String, String) -> Unit,
    private val ownerQuery: () -> Boolean,
    private val onSceneReloaded: () -> Unit,
    initialModelPath: String,
    initialModelJson: String,
) {
    private var currentPath = initialModelPath
    private var currentJson = initialModelJson
    private var surfaceEverCreated = false

    fun onSurfaceCreated() {
        // native 先行：fork 的 OnSurfaceCreate 有 view==NULL 守卫，顺序照旧。
        onSurfaceCreatedNative()
        if (!ownerQuery()) return
        val reloading = surfaceEverCreated
        surfaceEverCreated = true
        prepareScene(currentPath, currentJson)
        if (reloading) onSceneReloaded()
    }

    /** 上装并跟踪当前模型；[prepareScene] 抛出则不更新跟踪（屏幕上还是旧模型）。 */
    fun prepareAndTrack(modelPath: String, modelJson: String) {
        prepareScene(modelPath, modelJson)
        currentPath = modelPath
        currentJson = modelJson
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        onSurfaceChangedNative(width, height)
    }

    fun onDrawFrame() {
        onDrawFrameNative()
    }
}
