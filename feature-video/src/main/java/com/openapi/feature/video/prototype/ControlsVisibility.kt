package com.openapi.feature.video.prototype

/** 底部走带上会出现的按钮（顺序即渲染顺序）。 */
internal enum class BottomBarItem {
    PlayToggle,
    Subtitles,
    Speed,
    Danmaku,
    More,
    Favorite,
    Cast,
    Fullscreen,
}

/**
 * 控件显隐的判定层（纯函数，先于渲染钉死规则）。
 *
 * 弹幕只对在线源开放：本地文件没有弹幕源，chip 直接不出现，
 * 且 handler 侧对本地路径一律 no-op（防止旧 state / 其它入口把它打开）。
 */
internal object ControlsVisibility {

    /** 弹幕 chip 是否显示。 */
    fun danmakuChip(state: PlayerControlsState): Boolean = !state.local

    /** 弹幕开关是否允许切换（本地视频永远 false，兜底）。 */
    fun danmakuToggleApplies(local: Boolean, currentlyOn: Boolean): Boolean = !local

    /**
     * 底部走带的按钮清单。渲染按这份顺序出，测试也钉这份——
     * 上一集/下一集本页没有对应动作，不进清单（不留假键）。
     */
    fun bottomBar(state: PlayerControlsState): List<BottomBarItem> =
        listOf(
            BottomBarItem.PlayToggle,
            BottomBarItem.Subtitles,
            BottomBarItem.Speed,
            BottomBarItem.Danmaku,
            BottomBarItem.More,
            BottomBarItem.Favorite,
            BottomBarItem.Cast,
            BottomBarItem.Fullscreen,
        ).filter { it != BottomBarItem.Danmaku || danmakuChip(state) }
}
