package com.openapi.feature.video.prototype

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
}
