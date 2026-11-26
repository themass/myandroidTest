package com.openapi.feature.video.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「更多」面板的能力清单判定层。
 *
 * 参照 player/PlayerMoreMenu 的能力（投屏、解码器设置）＋本页真实有的分享与画中画；
 * 没有真件的（清晰度——仓库里不存在清晰度档位；Google Cast——绑定旧播放器类型）
 * 一律不进菜单，不留 toast 假按钮。
 */
class MoreMenuPolicyTest {

    @Test
    fun `local source offers share cast decoder and pip`() {
        assertEquals(
            listOf(MoreAction.Share, MoreAction.Cast, MoreAction.DecoderSettings, MoreAction.Pip),
            MoreMenuPolicy.actions(local = true),
        )
    }

    @Test
    fun `remote source keeps cast decoder pip but not share`() {
        // 分享走 local 的 content:// 方案（shareVideo），远程 url 没有可直接分享的本地件
        assertEquals(
            listOf(MoreAction.Cast, MoreAction.DecoderSettings, MoreAction.Pip),
            MoreMenuPolicy.actions(local = false),
        )
    }

    @Test
    fun `no fake entries - quality ladder does not exist`() {
        // 本菜单没有「清晰度」：feature-video 里不存在任何清晰度档位，EngineFallback 换的是内核
        assertEquals(4, MoreAction.entries.size)
    }
}
