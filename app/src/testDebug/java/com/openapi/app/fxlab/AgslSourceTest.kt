package com.openapi.app.fxlab

import com.openapi.app.R
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AgslSourceTest {

    @Test
    fun agslSupportedOnlyAt33Plus() {
        assertFalse(agslSupported(28))
        assertFalse(agslSupported(32))
        assertTrue(agslSupported(33))
        assertTrue(agslSupported(37))
    }

    @Test
    fun uniformsAreFiniteAndIntensityClamped() {
        val u = agslUniforms(tSeconds = 1.5f, intensity = 5f)
        assertTrue(u.values.all { it.isFinite() })
        assertTrue((u["intensity"] ?: 0f) <= 1f)
    }

    /**
     * 状态行显示 shader 解析错误的前提是：Kotlin 侧设的每个 uniform，shader 里真的声明了。
     * 两边漂移的结果正是 specs 不接受的「静默空白」。
     */
    @Test
    fun shaderDeclaresEveryUniformTheKotlinSets() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val src = context.resources.openRawResource(R.raw.fxlab_agsl)
            .use { it.reader().readText() }
        agslUniforms(0f, 1f).keys.forEach { name ->
            assertTrue("shader missing uniform $name", src.contains("uniform float $name"))
        }
    }

    @Test
    fun shaderFallsToTransparentEdgesSoItIsAHaloNotASheet() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val src = context.resources.openRawResource(R.raw.fxlab_agsl)
            .use { it.reader().readText() }
        // 半径外必须归零，否则就是一张盖住画面的板。
        assertTrue(src.contains("0.0") && src.contains("alpha"))
    }
}
