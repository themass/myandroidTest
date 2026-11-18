package com.openapi.ks.myapp.dh

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.openapi.ks.myapp.avatar.AvatarCapability
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.TouchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DhEngineRegistryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DhEngineRegistry.resetForTests()
        DhLabBootstrap.resetForTests()
        DhLabBootstrap.ensureDefaults()
    }

    @Test
    fun registered_simple_is_available_and_creatable() {
        val avail = DhEngineRegistry.available(context)
        val simple = avail.first { it.id == DhEngineId.SIMPLE }
        assertTrue(simple.available)
        val engine = DhEngineRegistry.create(context, DhEngineId.SIMPLE)
        assertEquals(DhEngineId.SIMPLE, engine.id)
    }

    @Test
    fun adapters_without_sdk_report_unavailable_reason() {
        val avail = DhEngineRegistry.available(context)
        for (id in listOf(DhEngineId.LIVE2D, DhEngineId.DUIX, DhEngineId.TAO_AVATAR)) {
            val row = avail.first { it.id == id }
            assertFalse(row.available)
            assertTrue(
                "reason for $id: ${row.reason}",
                row.reason?.contains("未编入") == true ||
                    row.reason?.contains("缺模型") == true ||
                    row.reason?.contains("机型不足") == true,
            )
        }
    }

    @Test
    fun create_truly_unregistered_throws() {
        DhEngineRegistry.resetForTests()
        // 只注册 SIMPLE，刻意不注册 DUIX
        DhEngineRegistry.register(DhEngineId.SIMPLE) { SimpleDigitalHumanEngine() }
        try {
            DhEngineRegistry.create(context, DhEngineId.DUIX)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("未编入") == true)
        }
    }

    @Test
    fun bridge_forwards_mouth_to_current_engine() {
        val fake = RecordingEngine(DhEngineId.SIMPLE)
        val bridge = DigitalHumanBridge(fake)
        bridge.onMouthLevel(0.7f)
        assertEquals(listOf(AvatarCommand.Mouth(0.7f)), fake.commands)
    }

    @Test
    fun available_does_not_retain_unreleased_engine_from_factory() {
        var created = 0
        var released = 0
        DhEngineRegistry.resetForTests()
        DhLabBootstrap.resetForTests()
        DhEngineRegistry.register(DhEngineId.SIMPLE) {
            created++
            object : DigitalHumanEngine {
                override val id = DhEngineId.SIMPLE
                override val capabilities: Set<AvatarCapability> = emptySet()
                override fun bind(spec: AvatarSpec) {}
                override fun dispatch(command: AvatarCommand) {}
                override fun release() { released++ }
                override fun availability(context: Context) =
                    DhEngineAvailability(DhEngineId.SIMPLE, available = true)
            }
        }
        DhEngineRegistry.available(context)
        assertEquals(1, created)
        assertEquals("probe engines from available() must be released", 1, released)
    }

    @Test
    fun bridge_swap_releases_old_and_forwards_to_new() {
        val old = RecordingEngine(DhEngineId.SIMPLE)
        val neu = RecordingEngine(DhEngineId.SIMPLE)
        val bridge = DigitalHumanBridge(old)
        bridge.swap(neu)
        assertEquals(1, old.releaseCount)
        bridge.onMouthLevel(0.2f)
        assertTrue(old.commands.isEmpty())
        assertEquals(listOf(AvatarCommand.Mouth(0.2f)), neu.commands)
    }

    @Test
    fun bridge_replace_with_releases_before_factory_runs() {
        val order = mutableListOf<String>()
        val old = object : DigitalHumanEngine by RecordingEngine(DhEngineId.SIMPLE) {
            override fun release() { order += "release" }
        }
        val bridge = DigitalHumanBridge(old)
        bridge.replaceWith {
            order += "create"
            RecordingEngine(DhEngineId.SIMPLE)
        }
        assertEquals(listOf("release", "create"), order)
    }

    private class RecordingEngine(override val id: DhEngineId) : DigitalHumanEngine {
        val commands = mutableListOf<AvatarCommand>()
        var releaseCount = 0
        override val capabilities: Set<AvatarCapability> = emptySet()
        override fun bind(spec: AvatarSpec) {}
        override fun dispatch(command: AvatarCommand) {
            commands += command
        }
        override fun onTouch(event: AvatarTouch): TouchResult = TouchResult.Ignored
        override fun release() {
            releaseCount++
        }
        override fun createView(context: Context): View? = null
        override fun availability(context: Context) =
            DhEngineAvailability(id, available = true)
    }
}
