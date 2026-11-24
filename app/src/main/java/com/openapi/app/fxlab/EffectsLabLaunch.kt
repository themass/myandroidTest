package com.openapi.app.fxlab

/**
 * 特效对比页的启动信息。放 **:app main**：生产导航只允许引用这两个 const，
 * 不能看到任何 debug-only 类型（specs §11：主源集只增加受 debuggable 保护的
 * 显式类名导航）。Activity 本体在 `app/src/debug`，release 清单合并不到它。
 */
object EffectsLabLaunch {
    const val ACTION = "com.openapi.app.action.EFFECTS_LAB"
    const val CLASS = "com.openapi.app.fxlab.EffectsLabActivity"
}
