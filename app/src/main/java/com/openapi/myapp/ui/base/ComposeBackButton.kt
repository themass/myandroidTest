package com.openapi.myapp.ui.base

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.openapi.ks.free1.R

/** 返回键图标距离屏幕左边的固定位置（图标本身 + 48dp 点击区内边距合计）。 */
private val BackEdge = 8.dp

/**
 * 所有页面统一的返回按钮：与 Toolbar 的 Up 箭头同形（Material ArrowBack），固定 24dp。
 *
 * 各页面的左边距不同（16 / 20 / 24dp），所以调用方传入自己的 [pagePadding]，
 * 按钮会向左偏移到同一位置：图标最终总是落在 20dp 处，与页面边距无关。
 */
@Composable
fun ComposeBackButton(onBack: () -> Unit, color: Color, pagePadding: Dp = 0.dp) {
    IconButton(
        onClick = onBack,
        modifier = Modifier.offset(x = BackEdge - pagePadding),
    ) {
        Icon(
            imageVector = ArrowBack,
            contentDescription = stringResource(R.string.password_back),
            tint = color,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Material ArrowBack 形状（自绘，避免新增 material-icons 依赖）。 */
private val ArrowBack: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArrowBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(20f, 11f)
        horizontalLineTo(7.83f)
        lineToRelative(5.59f, -5.59f)
        lineTo(12f, 4f)
        lineToRelative(-8f, 8f)
        lineToRelative(8f, 8f)
        lineToRelative(1.41f, -1.41f)
        lineTo(7.83f, 13f)
        horizontalLineTo(20f)
        verticalLineToRelative(-2f)
        close()
    }.build()
}
