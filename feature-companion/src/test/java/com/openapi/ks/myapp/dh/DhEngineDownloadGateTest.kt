package com.openapi.ks.myapp.dh

import com.openapi.ks.dh.EngineAssets
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DhEngineDownloadGateTest {

    @Test
    fun decide_unavailable_wins_over_everything() {
        assertEquals(EngineClickAction.UNAVAILABLE_REASON, decideEngineClick(ready = false, switches = false))
        assertEquals(EngineClickAction.UNAVAILABLE_REASON, decideEngineClick(ready = true, switches = false))
    }

    @Test
    fun decide_not_ready_but_available_starts_download() {
        assertEquals(EngineClickAction.NOT_READY_START_DOWNLOAD, decideEngineClick(ready = false, switches = true))
    }

    @Test
    fun decide_ready_and_available_selects() {
        assertEquals(EngineClickAction.SELECT, decideEngineClick(ready = true, switches = true))
    }

    @Test
    fun assets_mapping_covers_heavy_engines_and_skips_simple() {
        assertNull(DhEngineId.SIMPLE.assets())
        assertEquals(EngineAssets.DUIX, DhEngineId.DUIX.assets())
        assertEquals(EngineAssets.LIVE2D, DhEngineId.LIVE2D.assets())
        assertEquals(EngineAssets.TAO, DhEngineId.TAO_AVATAR.assets())
    }

    private fun gate(
        ready: Set<EngineAssets> = emptySet(),
        ensure: suspend (EngineAssets, (Float) -> Unit) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    ) = DhEngineDownloadGate(isReady = { it in ready }, ensure = ensure)

    private fun row(id: DhEngineId?, switches: Boolean = true, detail: String? = null) =
        DhEngineMenuRow(id = id, label = id?.name ?: "orig", switches = switches, detail = detail)

    @Test
    fun original_avatar_row_selects_without_download() {
        assertEquals(EngineClickAction.SELECT, gate().actionFor(row(null)))
    }

    @Test
    fun simple_has_no_assets_so_it_selects_instantly() {
        assertEquals(EngineClickAction.SELECT, gate().actionFor(row(DhEngineId.SIMPLE)))
    }

    @Test
    fun heavy_engine_not_ready_starts_download() {
        assertEquals(EngineClickAction.NOT_READY_START_DOWNLOAD, gate().actionFor(row(DhEngineId.DUIX)))
    }

    @Test
    fun heavy_engine_ready_selects() {
        val g = gate(ready = setOf(EngineAssets.DUIX))
        assertEquals(EngineClickAction.SELECT, g.actionFor(row(DhEngineId.DUIX)))
    }

    @Test
    fun unavailable_row_shows_reason_even_if_not_ready() {
        val g = gate()
        assertEquals(
            EngineClickAction.UNAVAILABLE_REASON,
            g.actionFor(row(DhEngineId.TAO_AVATAR, switches = false, detail = "设备不支持")),
        )
    }

    @Test
    fun download_success_reports_success_and_forwards_progress() = runTest {
        val seen = mutableListOf<Float>()
        val g = gate { assets, onProgress ->
            assertEquals(EngineAssets.DUIX, assets)
            onProgress(0.5f)
            onProgress(1f)
            Result.success(Unit)
        }
        val result = g.download(DhEngineId.DUIX) { seen += it }
        assertEquals(DhEngineDownloadGate.Outcome.Success, result)
        assertEquals(listOf(0.5f, 1f), seen)
        assertFalse(g.isDownloading(DhEngineId.DUIX))
    }

    @Test
    fun download_failure_carries_reason_and_clears_in_flight() = runTest {
        val g = gate { _, _ -> Result.failure(IOException("network down")) }
        val result = g.download(DhEngineId.LIVE2D) {}
        assertTrue(result is DhEngineDownloadGate.Outcome.Failed)
        assertEquals("network down", (result as DhEngineDownloadGate.Outcome.Failed).reason)
        assertFalse(g.isDownloading(DhEngineId.LIVE2D))
    }

    @Test
    fun download_failure_without_message_still_has_a_reason() = runTest {
        val g = gate { _, _ -> Result.failure(IllegalStateException()) }
        val result = g.download(DhEngineId.LIVE2D) {} as DhEngineDownloadGate.Outcome.Failed
        assertTrue(result.reason.isNotBlank())
    }

    @Test
    fun second_click_while_downloading_is_busy_and_does_not_start_again() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val g = gate { _, _ ->
            calls++
            release.await()
            Result.success(Unit)
        }
        val first = launch { g.download(DhEngineId.DUIX) {} }
        advanceUntilIdle()
        assertTrue(g.isDownloading(DhEngineId.DUIX))

        val second = g.download(DhEngineId.DUIX) {}
        assertEquals(DhEngineDownloadGate.Outcome.Busy, second)
        assertEquals(1, calls)

        release.complete(Unit)
        first.join()
        assertFalse(g.isDownloading(DhEngineId.DUIX))
    }

    @Test
    fun engine_without_assets_downloads_as_immediate_success() = runTest {
        var calls = 0
        val g = gate { _, _ -> calls++; Result.success(Unit) }
        assertEquals(DhEngineDownloadGate.Outcome.Success, g.download(DhEngineId.SIMPLE) {})
        assertEquals(0, calls)
    }
}
