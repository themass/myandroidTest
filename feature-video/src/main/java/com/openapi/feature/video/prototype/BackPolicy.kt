package com.openapi.feature.video.prototype

/** 返回键要做的三件事之一（判定与执行分开，便于单测钉死顺序）。 */
internal enum class BackIntent {
    /** 锁定态：只解锁，页面与全屏都不动。 */
    Unlock,

    /** 全屏（横屏）：退回竖屏，不关页。 */
    ExitFullscreen,

    /** 竖屏且未锁定：关闭页面。 */
    ClosePage,
}

/**
 * 新播放页返回键的判定层。
 *
 * targetSdk 37 + androidx.activity 1.11 的 predictive back 下，系统只走
 * OnBackPressedDispatcher，`Activity#onBackPressed` 的 override 会被完全绕过
 * （顶栏自绘返回按钮以前也直接调它，等于同样绕过）。所以返回逻辑抽成这份纯判定，
 * Activity 侧由常驻 OnBackPressedCallback 与顶栏按钮共用。
 */
internal object BackPolicy {
    fun decide(locked: Boolean, fullscreen: Boolean): BackIntent = when {
        locked -> BackIntent.Unlock
        fullscreen -> BackIntent.ExitFullscreen
        else -> BackIntent.ClosePage
    }
}
