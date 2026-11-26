package com.openapi.chatkit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图片加载/重试状态机（整改清单 P1-10）的转移表测试。
 *
 * UI 侧（ImageGrid / ImageViewer / mediaview 卡片）只负责把 Coil 的成功 / 失败回调和
 * 重试点击喂进来，全部行为以这里的转移为准；失败分支（失败 → 点击 → 重试中 →
 * 又失败 / 成功）不依赖 Compose，纯 JVM 覆盖。
 */
class ImageLoadStateTest {

    @Test
    fun naturalLoadFailuresShowFailed() {
        val after = ImageLoadState().onFailed()
        assertEquals(ImageVisualStatus.Failed, after.status)
        assertEquals("自然失败不消耗重试次数", 0, after.attempt)
    }

    @Test
    fun naturalLoadSuccessShowsSuccess() {
        assertEquals(ImageVisualStatus.Success, ImageLoadState().onLoaded().status)
    }

    @Test
    fun retryClickFromFailedStartsRetryingAndBumpsAttempt() {
        val failed = ImageLoadState().onFailed()
        val retrying = failed.onRetryClick()
        assertEquals(ImageVisualStatus.Retrying, retrying.status)
        assertEquals("点一次重试 attempt+1（UI 拿它换 Coil 缓存 key）", 1, retrying.attempt)
    }

    @Test
    fun retrySucceedingGoesBackToSuccessKeepingAttempt() {
        val state = ImageLoadState().onFailed().onRetryClick().onLoaded()
        assertEquals(ImageVisualStatus.Success, state.status)
        assertEquals("成功后 attempt 不回退：下一次重试仍要换 key", 1, state.attempt)
    }

    @Test
    fun retryFailingAgainGoesBackToFailedKeepingAttempt() {
        val state = ImageLoadState().onFailed().onRetryClick().onFailed()
        assertEquals(ImageVisualStatus.Failed, state.status)
        assertEquals("第二次重试失败后，再点一次 attempt 变 2", 1, state.attempt)
        assertEquals(2, state.onRetryClick().attempt)
    }

    @Test
    fun retryClickOutsideFailedIsIgnored() {
        // 没有失败可重试 / 已在途：点击不该改变任何东西（防连点在状态机层的兜底）
        for (start in listOf(ImageLoadState(), ImageLoadState().onLoaded(), ImageLoadState().onFailed().onRetryClick())) {
            assertEquals("忽略后原样返回", start, start.onRetryClick())
        }
    }

    @Test
    fun staleCallbacksAfterSuccessAreIgnored() {
        // 迟到的旧回调（换源后旧请求才回来）不能把已成功的图翻回失败
        val success = ImageLoadState().onLoaded()
        assertEquals(ImageVisualStatus.Success, success.onFailed().status)
    }

    @Test
    fun staleLoadedCallbackCannotHealAFailedImage() {
        // 失败态下迟到的旧成功同样被忽略：只有用户点重试才能走出失败
        val failed = ImageLoadState().onFailed()
        assertEquals(ImageVisualStatus.Failed, failed.onLoaded().status)
    }

    // ---- A43-5：历史签名 URL（300 秒）过期时「先静默重签一次」的转移 ----

    @Test
    fun autoResignKeepsTheSlotLoadingAndBumpsAttempt() {
        val after = ImageLoadState().onFailedAwaitingResign()
        assertEquals("自动重签不进失败占位：留在加载态", ImageVisualStatus.Loading, after.status)
        assertEquals("attempt 必须 +1：重签可能拿回同一地址，不换 Coil key 就不会再发请求", 1, after.attempt)
    }

    @Test
    fun autoResignFromRetryingAlsoBumpsAttempt() {
        val retrying = ImageLoadState().onFailed().onRetryClick()
        val after = retrying.onFailedAwaitingResign()
        assertEquals(ImageVisualStatus.Retrying, after.status)
        assertEquals(2, after.attempt)
    }

    @Test
    fun autoResignOutsideLoadingOrRetryingIsIgnored() {
        // 已 Failed / 已 Success：自动重签不复活它们（一世一次的闸在调用方按格记账，
        // 这里只是转移表自身的兜底，语义与迟到的旧回调一致）
        val failed = ImageLoadState().onFailed()
        assertEquals(failed, failed.onFailedAwaitingResign())
        val success = ImageLoadState().onLoaded()
        assertEquals(success, success.onFailedAwaitingResign())
    }

    @Test
    fun resignableSourceCoversRemoteUrlsAndEmptyFallbackSlots() {
        assertTrue(isResignableImageSource("https://signed.example/u1"))
        assertTrue(isResignableImageSource("http://signed.example/u2"))
        // 换源失败退化出的空 path 槽（ChatLogMapper 的 REMOTE 标记）：仍值得再换一次
        assertTrue(isResignableImageSource(""))
    }

    @Test
    fun localFilePathIsNotResignable() {
        // 本机文件没有可换的地址：组件自己的强制重载已经是全部能做的事
        assertFalse(isResignableImageSource("/data/data/pkg/files/a.jpg"))
        assertFalse(isResignableImageSource("content://media/external/images/1"))
    }
}
