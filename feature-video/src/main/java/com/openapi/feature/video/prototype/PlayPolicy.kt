package com.openapi.feature.video.prototype

import com.shuyu.gsyvideoplayer.video.base.GSYVideoView

/** 播放/暂停键要做的三件事之一（判定与执行分开，便于单测钉死）。 */
internal enum class PlayIntent {
    /** 正在播：暂停。 */
    Pause,

    /** 暂停/准备中：继续（GSY 的 onVideoResume 只处理 PAUSE=5）。 */
    Resume,

    /** 自动播完（state 6）：纹理视图已被移除，必须全量重挂。 */
    Restart,
}

/**
 * 播完再点的判定层。
 *
 * GSY 13.1.0 自动播完时把状态置 [GSYVideoView.CURRENT_STATE_AUTO_COMPLETE]（6）并移除
 * 纹理视图，之后 `onVideoResume()` 是空操作（它只认 [GSYVideoView.CURRENT_STATE_PAUSE]），
 * 所以旧逻辑「播完再点没反应」。这里把 6 单独判出来，走 setUp + startPlayLogic 重挂。
 */
internal object PlayPolicy {

    fun decide(state: Int, userWantsPlay: Boolean): PlayIntent = when {
        !userWantsPlay -> PlayIntent.Pause
        state == GSYVideoView.CURRENT_STATE_AUTO_COMPLETE -> PlayIntent.Restart
        else -> PlayIntent.Resume
    }

    /** GSY 的「正在播」判定与 [NewVideoShowActivity] 的 playing 保持一致。 */
    fun isPlaying(state: Int): Boolean =
        state == GSYVideoView.CURRENT_STATE_PLAYING ||
            state == GSYVideoView.CURRENT_STATE_PLAYING_BUFFERING_START
}
