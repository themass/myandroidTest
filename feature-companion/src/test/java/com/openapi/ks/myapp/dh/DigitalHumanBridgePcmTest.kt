package com.openapi.ks.myapp.dh

import android.content.Context
import android.view.View
import com.openapi.ks.myapp.avatar.AvatarCapability
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.TouchResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DigitalHumanBridgePcmTest {

    @Test
    fun onRemotePcm_forwards_to_current_engine() {
        val engine = PcmRecordingEngine()
        val bridge = DigitalHumanBridge(engine)
        val pcm = byteArrayOf(1, 2, 3, 4)
        bridge.onRemotePcm(pcm)
        assertEquals(1, engine.pcmBatches.size)
        assertArrayEquals(pcm, engine.pcmBatches[0])
    }

    @Test
    fun onRemotePcm_after_replace_goes_to_new_engine() {
        val old = PcmRecordingEngine()
        val neu = PcmRecordingEngine()
        val bridge = DigitalHumanBridge(old)
        bridge.replaceWith { neu }
        bridge.onRemotePcm(byteArrayOf(9))
        assertTrue(old.pcmBatches.isEmpty())
        assertEquals(1, neu.pcmBatches.size)
    }

    private class PcmRecordingEngine : DigitalHumanEngine {
        val pcmBatches = mutableListOf<ByteArray>()
        override val id = DhEngineId.DUIX
        override val capabilities: Set<AvatarCapability> = emptySet()
        override fun bind(spec: AvatarSpec) {}
        override fun dispatch(command: AvatarCommand) {}
        override fun onTouch(event: AvatarTouch): TouchResult = TouchResult.Ignored
        override fun release() {}
        override fun createView(context: Context): View? = null
        override fun availability(context: Context) =
            DhEngineAvailability(id, available = true)
        override fun pushRemotePcm(pcm16leMono16k: ByteArray) {
            pcmBatches += pcm16leMono16k.copyOf()
        }
    }
}
