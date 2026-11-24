package com.openapi.feature.camerafx

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 头顶贴纸的**渲染注入口**（可组合边界上的一个参数，不是策略、不是事件总线）。
 *
 * 相机侧继续拥有全部几何：[StickerPlacementPx] 由 `CanvasMap` + `stickerAtHeadTop`
 * + EMA + 丢失容错算出，注入方只拿现成的位置/尺寸/倾角画自己的后端，
 * **不得重算几何**（specs §11）。null = 走今天的 Lottie 分支，生产调用点全部传 null。
 *
 * 本模块不 import 任何新的 runtime 库：实现方（Rive/GL/WebView…）在 :app 的
 * debug source set 里注入，release 包里物理上不存在这些后端。
 */
typealias CameraFxStickerRenderer = @Composable (placement: StickerPlacementPx, modifier: Modifier) -> Unit

/**
 * 分支规则本身（纯函数，JVM 钉死）：注入了 renderer 就用它——即使 Lottie 组合还在
 * 加载，注入的后端也不该跟着那个加载状态走；没注入就保持今天的 Lottie / 粉圈兜底分支。
 */
fun stickerRendererTakesOverWhenSet(rendererSet: Boolean, compLoaded: Boolean): Boolean = rendererSet
