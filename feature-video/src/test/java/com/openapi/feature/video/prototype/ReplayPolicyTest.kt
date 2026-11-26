package com.openapi.feature.video.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 播完再点必须走全量重挂，而不是 onVideoResume。
 * GSY 自动播完会把状态置 CURRENT_STATE_AUTO_COMPLETE(6) 并移除纹理视图，
 * onVideoResume 只处理 PAUSE(5)，所以状态 6 下 resume 是空操作——点了没反应。
 */
class ReplayPolicyTest {

    @Test
    fun `auto-completed playback restarts from scratch`() {
        assertEquals(
            PlayIntent.Restart,
            PlayPolicy.decide(state = GSY_STATE_AUTO_COMPLETE, userWantsPlay = true),
        )
    }

    @Test
    fun `playing video pauses`() {
        assertEquals(
            PlayIntent.Pause,
            PlayPolicy.decide(state = GSY_STATE_PLAYING, userWantsPlay = false),
        )
    }

    @Test
    fun `paused video resumes`() {
        assertEquals(
            PlayIntent.Resume,
            PlayPolicy.decide(state = GSY_STATE_PAUSE, userWantsPlay = true),
        )
    }

    @Test
    fun `idle or preparing video starts playing`() {
        assertEquals(
            PlayIntent.Resume,
            PlayPolicy.decide(state = GSY_STATE_NORMAL, userWantsPlay = true),
        )
        assertEquals(
            PlayIntent.Resume,
            PlayPolicy.decide(state = GSY_STATE_PREPAREING, userWantsPlay = true),
        )
    }

    private companion object {
        // GSYVideoView 的状态常量名（13.1.0）；测试里用字面值钉住，避免改常量名时无声通过
        const val GSY_STATE_NORMAL = 0
        const val GSY_STATE_PREPAREING = 1
        const val GSY_STATE_PLAYING = 2
        const val GSY_STATE_PAUSE = 5
        const val GSY_STATE_AUTO_COMPLETE = 6
    }
}
