package com.openapi.ks.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EGL 重建后场景重载决策层（真机潜在白屏，2026-11-28 P0-A 排查取证）。
 *
 * 旧实现：`modelLoadedOnGl` 闩锁让 prepareLive2dScene 只在**首次** onSurfaceCreated
 * 跑一次。GLSurfaceView 默认不保留 EGL context——onPause/onResume（后台回前台）、
 * surface 重建之后，Cubism 模型手里的纹理/着色器句柄全部失效，且没有任何重载路径：
 * 舞台白/黑且永不自愈。修复=每次 surface 创建都重新上装「当前」模型
 * （热切换后不回落初始模型），被逐出归属的旧宿主不得重建共享场景。
 *
 * 纯 JVM：native/GL 全部是注入的 lambda，不触 JNI。
 */
class Live2dGlSceneRendererTest {

    private class Recorder {
        val calls = mutableListOf<String>()
        var prepareThrows = false

        fun renderer(
            owner: Boolean = true,
            initialPath: String = "/m/ATRI/",
            initialJson: String = "ATRI.model3.json",
        ): Pair<Live2dGlSceneRenderer, (Boolean) -> Unit> {
            var ownerState = owner
            val r = Live2dGlSceneRenderer(
                onSurfaceCreatedNative = { calls += "nativeCreated" },
                onSurfaceChangedNative = { w, h -> calls += "nativeChanged:$w,$h" },
                onDrawFrameNative = { calls += "nativeDraw" },
                prepareScene = { path, json ->
                    if (prepareThrows) throw IllegalStateException("native load failed")
                    calls += "prepare:$path$json"
                },
                ownerQuery = { ownerState },
                onSceneReloaded = { calls += "reloaded" },
                initialModelPath = initialPath,
                initialModelJson = initialJson,
            )
            return r to { o: Boolean -> ownerState = o }
        }
    }

    @Test
    fun `首次 surface 创建上装初始模型，native 回调先行`() {
        val rec = Recorder()
        val (r, _) = rec.renderer()
        r.onSurfaceCreated()
        assertEquals(listOf("nativeCreated", "prepare:/m/ATRI/ATRI.model3.json"), rec.calls)
    }

    @Test
    fun `EGL 重建（第二次 onSurfaceCreated）必须重新上装场景——旧闩锁不重载就是永久白屏`() {
        val rec = Recorder()
        val (r, _) = rec.renderer()
        r.onSurfaceCreated()
        r.onSurfaceCreated() // surface/EGL context 重建
        assertEquals(2, rec.calls.count { it.startsWith("prepare:") })
        // 重建上装后要通知宿主复位 GL 态闩锁（gaze capture 等），首次不算重建。
        assertEquals(listOf("reloaded"), rec.calls.filter { it == "reloaded" })
    }

    @Test
    fun `热切换后重建上装的是新模型，不回落初始模型`() {
        val rec = Recorder()
        val (r, _) = rec.renderer()
        r.onSurfaceCreated()
        r.prepareAndTrack("/m/Yuuka/", "Yuuka.model3.json")
        r.onSurfaceCreated()
        assertEquals(
            listOf(
                "prepare:/m/ATRI/ATRI.model3.json",
                "prepare:/m/Yuuka/Yuuka.model3.json",
                "prepare:/m/Yuuka/Yuuka.model3.json",
            ),
            rec.calls.filter { it.startsWith("prepare:") },
        )
    }

    @Test
    fun `切换失败不记录新模型——native 先载后拆，屏幕上还是旧模型`() {
        val rec = Recorder()
        val (r, _) = rec.renderer()
        r.onSurfaceCreated()
        rec.prepareThrows = true
        runCatching { r.prepareAndTrack("/m/Broken/", "Broken.model3.json") }
        rec.prepareThrows = false
        r.onSurfaceCreated()
        assertEquals(
            listOf(
                "prepare:/m/ATRI/ATRI.model3.json",
                "prepare:/m/ATRI/ATRI.model3.json",
            ),
            rec.calls.filter { it.startsWith("prepare:") },
        )
    }

    @Test
    fun `被逐出归属的旧宿主不重建进程级共享场景，native surface 事件照常透传`() {
        val rec = Recorder()
        val (r, setOwner) = rec.renderer(owner = true)
        setOwner(false) // 后来者 claim 接管（onEvictedFromNativeScene 之后）
        r.onSurfaceCreated()
        assertTrue(rec.calls.none { it.startsWith("prepare:") })
        assertTrue(rec.calls.contains("nativeCreated"))
    }

    @Test
    fun `surfaceChanged 与 drawFrame 原样透传`() {
        val rec = Recorder()
        val (r, _) = rec.renderer()
        r.onSurfaceChanged(1080, 1920)
        r.onDrawFrame()
        assertEquals(listOf("nativeChanged:1080,1920", "nativeDraw"), rec.calls)
    }
}
