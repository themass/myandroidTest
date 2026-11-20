package com.openapi.ks.myapp.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 请求的是 native 引擎却落在 SIMPLE 时，要告诉用户为什么（不能只剩一个「形象准备中」）。 */
class DhDegradeNoticeTest {

    @Test
    fun no_notice_when_engine_is_what_was_asked_for() {
        assertNull(DhDegradeNotice.text(DhEngineId.LIVE2D, DhEngineId.LIVE2D, quarantined = false))
        assertNull(DhDegradeNotice.text(DhEngineId.SIMPLE, DhEngineId.SIMPLE, quarantined = false))
    }

    @Test
    fun quarantine_explains_a_recent_crash_and_how_to_retry() {
        val t = DhDegradeNotice.text(DhEngineId.TAO_AVATAR, DhEngineId.SIMPLE, quarantined = true)!!
        assertTrue(t.contains("异常"))
        assertTrue(t.contains("菜单"))
    }

    @Test
    fun unavailable_engine_says_it_is_not_available_on_this_device() {
        val t = DhDegradeNotice.text(DhEngineId.TAO_AVATAR, DhEngineId.SIMPLE, quarantined = false)!!
        assertTrue(t.contains("不可用"))
    }

    @Test
    fun native_engine_swapped_for_another_native_engine_needs_no_notice() {
        assertNull(DhDegradeNotice.text(DhEngineId.DUIX, DhEngineId.LIVE2D, quarantined = false))
    }
}
