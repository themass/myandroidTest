package com.openapi.ks.myapp.dh

import android.view.TextureView
import android.view.View

/**
 * 通话页主题色有时要设到 Android View 上做占位底（在 Compose 渐变之上、GL 之下）。
 * [TextureView]（Duix）不支持 background drawable，[View.setBackgroundColor] 会抛
 * `UnsupportedOperationException: TextureView doesn't support displaying a background drawable`。
 */
fun canApplyThemeBackgroundToView(view: View): Boolean = view !is TextureView
