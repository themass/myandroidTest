package com.openapi.ks.myapp.dh

import android.view.SurfaceView
import android.view.TextureView
import android.view.View

/**
 * 普通 View 可用主题色做占位底；两种独立渲染视口必须交给各自的渲染器清屏。
 * [TextureView]（Duix）不支持 background drawable，会抛 UnsupportedOperationException。
 * [SurfaceView]（含 Live2D GLSurfaceView）在窗口画布上挖透明洞后调用 super.draw；
 * 不透明 background 会重新填满洞，挡住下层 GL 内容。Live2D 已经通过 JNI 设置清屏色。
 * Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/SurfaceView.java
 */
fun canApplyThemeBackgroundToView(view: View): Boolean =
    view !is TextureView && view !is SurfaceView
