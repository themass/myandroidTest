package com.openapi.ks.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 进程级 native 场景归属（真机取证 2026-11-25 ADY-AL00，pid 14349）。
 *
 * `libchatwaifu-live2d.so` 的 LAppDelegate/LAppLive2DManager 是进程全局单例：native 场景
 * 同一时刻只有一份，但堆上可以活着多个 [Live2dRuntime]。第二个实例 nativeOnStart 换掉
 * 全局 LAppView 而第一个实例的 GLThread 还在逐帧 Run → SIGSEGV（fault addr 0x14，
 * `LAppView::Render` 里 `_back->Render()`，02:43:23）；第一个实例的 nativeOnStop 又会
 * CubismFramework::Dispose 掉第二个实例正在显示的场景（加载成功但画不出人）。
 *
 * 守卫规则（本对象钉死，纯 JVM 可测）：
 * ① 后来者 claim 接管，并拿到被逐出的旧主人（由它负责跑旧主人的 native 拆除）；
 * ② 旧主人的 release 不得清空当前主人的归属；
 * ③ 主人自己 release 后归属清空。
 */
class Live2dNativeOwnershipTest {

    @Test
    fun `首个 claim 没有前任`() {
        val o = Live2dNativeOwnership()
        val a = Any()
        assertNull(o.claim(a))
        assertTrue(o.isOwner(a))
    }

    @Test
    fun `后来者 claim 接管并交出前任`() {
        val o = Live2dNativeOwnership()
        val a = Any()
        val b = Any()
        o.claim(a)
        assertEquals(a, o.claim(b))
        assertTrue(o.isOwner(b))
        assertFalse(o.isOwner(a))
    }

    @Test
    fun `被逐出的旧主人 release 不清空当前归属`() {
        val o = Live2dNativeOwnership()
        val a = Any()
        val b = Any()
        o.claim(a)
        o.claim(b)
        assertFalse(o.release(a))
        assertTrue("b 仍然是 native 场景的主人", o.isOwner(b))
    }

    @Test
    fun `主人 release 清空归属后可以再 claim`() {
        val o = Live2dNativeOwnership()
        val a = Any()
        o.claim(a)
        assertTrue(o.release(a))
        assertFalse(o.isOwner(a))
        assertNull(o.claim(Any()))
    }

    @Test
    fun `Live2dRuntime 接线：OnStart 前 claim，native 拆除只属于当前主人`() {
        // buildGlView 必须先 claim 再 nativeOnStart（fork v2 的 OnStart 幂等守卫配合），
        // release 里的 nativeOnStop/nativeOnDestroy 必须在归属判定之后——被逐出的旧实例
        // 若照样拆共享单例，新主人画面当场消失（这就是崩溃与互踩的 Kotlin 侧根因）。
        // 与 release_pauses_the_gl_thread_before_native_teardown 同款源码顺序断言。
        var dir = java.io.File(System.getProperty("user.dir"))
        while (dir != null && !java.io.File(dir, "settings.gradle").isFile) dir = dir.parentFile
        val src = java.io.File(checkNotNull(dir), "dh-engines/src/main/java/com/openapi/ks/dh/Live2dRuntime.kt").readText()

        val build = src.substringAfter("private fun buildGlView")
        val claim = build.indexOf("nativeOwnership.claim(this)")
        assertTrue("buildGlView must claim native ownership", claim >= 0)
        assertTrue("claim must precede nativeOnStart", build.indexOf("nativeOnStart()") > claim)

        val rel = src.substringAfter("fun release()")
        val gate = rel.indexOf("nativeOwnership.release(this)")
        assertTrue("release must consult native ownership before native teardown", gate >= 0)
        assertTrue("nativeOnStop only for the current owner", rel.indexOf("nativeOnStop()") > gate)
        assertTrue("nativeOnDestroy only for the current owner", rel.indexOf("nativeOnDestroy()") > gate)
    }
}
