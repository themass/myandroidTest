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
    fun decide_unavailable_and_no_download_route_shows_reason() {
        assertEquals(
            EngineClickAction.UNAVAILABLE_REASON,
            decideEngineClick(available = false, switches = false),
        )
    }

    @Test
    fun decide_cold_engine_but_downloadable_starts_download() {
        assertEquals(
            EngineClickAction.NOT_READY_START_DOWNLOAD,
            decideEngineClick(available = false, switches = true),
        )
    }

    @Test
    fun decide_available_selects() {
        assertEquals(EngineClickAction.SELECT, decideEngineClick(available = true, switches = true))
    }

    @Test
    fun assets_mapping_covers_heavy_engines_and_skips_simple() {
        assertNull(DhEngineId.SIMPLE.assets())
        assertEquals(EngineAssets.DUIX, DhEngineId.DUIX.assets())
        assertEquals(EngineAssets.LIVE2D, DhEngineId.LIVE2D.assets())
        assertEquals(EngineAssets.TAO, DhEngineId.TAO_AVATAR.assets())
        assertEquals(EngineAssets.VRM, DhEngineId.VRM.assets())
    }

    // 就绪判断现在是「引擎自判可用」（availability），不再是 AssetStore.isReady：
    // - Live2D 清单故意留空，store.isReady 永远 false，引擎自判可用就必须能选（Final review I1）。
    // - 素材下完但 native 缺失时引擎自判不可用，换不换舞台由调用方下载后重新确认（Final review C1）。
    private fun gate(
        available: Set<DhEngineId> = emptySet(),
        ensure: suspend (EngineAssets, (Float) -> Unit) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    ) = DhEngineDownloadGate(availability = { it in available }, ensure = ensure)

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
    fun heavy_engine_not_available_starts_download() {
        assertEquals(
            EngineClickAction.NOT_READY_START_DOWNLOAD,
            gate().actionFor(row(DhEngineId.DUIX)),
        )
    }

    @Test
    fun live2d_no_online_pack_shows_reason_not_fake_download() {
        assertEquals(
            EngineClickAction.UNAVAILABLE_REASON,
            gate().actionFor(row(DhEngineId.LIVE2D)),
        )
    }

    @Test
    fun engine_available_by_its_own_check_selects_even_with_empty_asset_manifest() {
        // ATRI 是 adb 推的、清单为空：AssetStore 永远说没就绪，但引擎自判可用 → 必须能直接选。
        val g = gate(available = setOf(DhEngineId.LIVE2D))
        assertEquals(EngineClickAction.SELECT, g.actionFor(row(DhEngineId.LIVE2D)))
    }

    @Test
    fun available_wins_over_stale_downloadable_row() {
        // 菜单行还是旧的（下载后没刷新），但引擎自判已可用 → 直接换舞台，不再重复下载。
        val g = gate(available = setOf(DhEngineId.DUIX))
        assertEquals(EngineClickAction.SELECT, g.actionFor(row(DhEngineId.DUIX, switches = true)))
    }

    @Test
    fun not_available_and_not_downloadable_shows_reason_even_with_downloadable_row_stale() {
        val g = gate(available = emptySet())
        assertEquals(
            EngineClickAction.UNAVAILABLE_REASON,
            g.actionFor(row(DhEngineId.TAO_AVATAR, switches = false, detail = "机型不足")),
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
