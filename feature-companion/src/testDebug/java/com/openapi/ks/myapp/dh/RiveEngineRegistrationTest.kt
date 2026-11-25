package com.openapi.ks.myapp.dh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.openapi.ks.myapp.avatar.AvatarCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rive 官方 Android 运行时进通话舞台（lab only，debug 构建）。
 *
 * 注册走 [DhLabBootstrap] 对 debug 源集的反射钩子：release 里 [RiveDigitalHumanEngine]
 * 与 rive 运行时都不存在，钩子静默失败 = 「未编入」。本测试在 testDebug 源集：
 * 只有 debug 单测变体的类路径上有 rive-android 与 debug 注册器。
 *
 * 样本 .riv 按许可结论留在 :app 的 debug res（README-fxlab-rive.md），引擎按资源名
 * 运行时查找——feature-companion 的单测看不到 :app 资源，所以这里钉的是「debug 变体
 * 已注册」+「release 不含」，真机 debug 包里样本在场即整行可用。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RiveEngineRegistrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DhEngineRegistry.resetForTests()
        DhLabBootstrap.resetForTests()
        DhLabBootstrap.ensureDefaults()
    }

    @Test
    fun debug_variant_registers_rive_not_uncompiled() {
        val row = DhEngineRegistry.available(context).first { it.id == DhEngineId.RIVE }
        assertTrue("RIVE must be registered in debug variant: ${row.reason}", row.reason != "未编入")
        // 样本在 :app debug res，模块单测里查不到：此时报「缺样本」而不是未编入。
        val engine = DhEngineRegistry.create(context, DhEngineId.RIVE)
        assertEquals(DhEngineId.RIVE, engine.id)
        engine.release()
        assertNull(DhEngineId.RIVE.assets())
    }

    @Test
    fun reflection_hook_target_name_matches_the_debug_registrar_class() {
        // main 里的 FQCN 常量与 debug 源集的注册器必须一字不差——改名两侧一起改。
        assertNotNull(Class.forName(DhLabBootstrap.DEBUG_ENGINE_REGISTRAR))
    }

    @Test
    fun release_variant_has_no_rive_classes() {
        // 反射钩子按名字找类；release 里 debug 类不存在。这里钉类名不在 main 产物里：
        // feature-companion 的 main 编译类路径（testDebug 也在的）不含引擎类本身时，
        // 下面这行应当抛——但 testDebug 变体在，所以断言方向是「main 源码树不含」。
        val mainSrc = java.io.File(
            System.getProperty("module.dir", System.getProperty("user.dir")),
            "src/main/java/com/openapi/ks/myapp/dh/RiveDigitalHumanEngine.kt",
        )
        assertTrue("Rive engine must not live in src/main: $mainSrc", !mainSrc.exists())
    }

    @Test
    fun rive_engine_lifecycle_never_throws_even_without_native() {
        val engine = RiveDigitalHumanEngine()
        // Robolectric 里 Rive native 未必起得来：引擎必须兜住（fxlab 同纪律），
        // 起不来就回 null（给不出可渲染面就不给 View），但绝不抛。
        engine.createView(context)
        engine.dispatch(AvatarCommand.Gesture("wave"))
        engine.release()
        engine.release()
        assertNull(engine.createView(context))
    }

    @Test
    fun rive_capabilities_are_empty_official_fixture_has_no_channels() {
        // 官方 loopy/circle_move 只有循环动画：没有情绪/口型/注视通道，capabilities 必须诚实为空。
        assertEquals(emptySet<com.openapi.ks.myapp.avatar.AvatarCapability>(), RiveDigitalHumanEngine().capabilities)
    }
}
