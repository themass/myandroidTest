package com.openapi.ks.myapp.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DhEngineUnavailableCopyTest {

    @Test
    fun live2d_menu_detail_mentions_seed() {
        val d = dhEngineMenuDetail(
            DhEngineId.LIVE2D,
            available = false,
            reason = "未下载",
            needsDownload = true,
        )
        assertTrue(d!!.contains("seed"))
    }

    @Test
    fun map_ensure_empty_manifest_to_seed_hint() {
        val m = mapEnsureFailureMessage(
            DhEngineId.LIVE2D,
            "No downloadable assets are configured for LIVE2D",
        )
        assertTrue(m.contains("seed-assets"))
    }

    @Test
    fun duix_keeps_server_error() {
        assertEquals("network down", mapEnsureFailureMessage(DhEngineId.DUIX, "network down"))
    }
}
