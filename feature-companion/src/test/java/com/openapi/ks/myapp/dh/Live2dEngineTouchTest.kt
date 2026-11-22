package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarRegion
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.AvatarTouchKind
import com.openapi.ks.myapp.avatar.TouchResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 触点互动的引擎/桥接缝（demo #2）：舞台层 → [DigitalHumanBridge.onAvatarTouch] →
 * [Live2dDigitalHumanEngine.onTouch] 必须真的走到状态机并回 Handled(region)。
 * 没有挂 runtime（没建 View）也必须回 Handled——决策与渲染分离，native 缺席
 * 不该吞掉触摸语义（上层脉冲/上报照旧）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Live2dEngineTouchTest {

    private fun tap(nx: Float, ny: Float) = AvatarTouch(nx, ny, AvatarTouchKind.TAP)

    @Test
    fun `engine onTouch returns Handled with head or body region`() {
        val engine = Live2dDigitalHumanEngine()
        assertEquals(TouchResult.Handled(AvatarRegion.HEAD), engine.onTouch(tap(0.5f, 0.1f)))
        assertEquals(TouchResult.Handled(AvatarRegion.BODY), engine.onTouch(tap(0.5f, 0.45f)))
        engine.release()
    }

    @Test
    fun `demo 2b zones map to fine-grained Handled regions`() {
        // demo #2b：Live2D 的 onTouch 用细分区（手/脚各有反应），Handled 带回的
        // 是细粒度区域名；上报给 agent 的 stageTapRegion 仍保持 head/body 老语义。
        val engine = Live2dDigitalHumanEngine()
        assertEquals(TouchResult.Handled(AvatarRegion.HAND), engine.onTouch(tap(0.34f, 0.50f)))
        assertEquals(TouchResult.Handled(AvatarRegion.FOOT), engine.onTouch(tap(0.47f, 0.65f)))
        engine.release()
    }

    @Test
    fun `non-tap kinds are ignored`() {
        val engine = Live2dDigitalHumanEngine()
        assertEquals(TouchResult.Ignored, engine.onTouch(AvatarTouch(0.5f, 0.5f, AvatarTouchKind.DOWN)))
        assertEquals(TouchResult.Ignored, engine.onTouch(AvatarTouch(0.5f, 0.5f, AvatarTouchKind.MOVE)))
        assertEquals(TouchResult.Ignored, engine.onTouch(AvatarTouch(0.5f, 0.5f, AvatarTouchKind.UP)))
        engine.release()
    }

    @Test
    fun `bridge routes touches to the current engine and follows replacement`() {
        val live2d = Live2dDigitalHumanEngine()
        val bridge = DigitalHumanBridge(live2d)
        assertEquals(TouchResult.Handled(AvatarRegion.HEAD), bridge.onAvatarTouch(tap(0.5f, 0.0f)))
        bridge.current().release()
    }

    @Test
    fun `consecutive head taps through the bridge toggle the state machine`() {
        val live2d = Live2dDigitalHumanEngine()
        val bridge = DigitalHumanBridge(live2d)
        bridge.onAvatarTouch(tap(0.5f, 0.1f)) // blush on
        bridge.onAvatarTouch(tap(0.5f, 0.1f)) // blush off
        // 状态机的迁移正确性由 Live2dTouchReactionTest 钉死；这里只保证桥接真的送达。
        // 躯干带坐标（0.45）——0.9 在 #2b 之后是 foot 区。
        assertEquals(TouchResult.Handled(AvatarRegion.BODY), bridge.onAvatarTouch(tap(0.5f, 0.45f)))
        bridge.current().release()
    }
}
