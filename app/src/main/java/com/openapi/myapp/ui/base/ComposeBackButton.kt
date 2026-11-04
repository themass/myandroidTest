package com.openapi.myapp.ui.base

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.openapi.ks.free1.R

/**
 * 所有 Compose 页面统一的返回按钮：样式、字号、颜色与点击区域一致。
 * 页面顶部的位置由调用方决定（放在 Row 或 Column 中均可）。
 */
@Composable
fun ComposeBackButton(onBack: () -> Unit, color: Color) {
    TextButton(onClick = onBack) {
        Text(
            text = "←  " + stringResource(R.string.password_back),
            color = color,
            fontSize = 15.sp,
        )
    }
}
