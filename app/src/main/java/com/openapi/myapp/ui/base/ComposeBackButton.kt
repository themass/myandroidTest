package com.openapi.myapp.ui.base

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.openapi.ks.free1.R

/**
 * 所有页面统一的返回按钮：与 Toolbar 的 Up 箭头同形（Material ArrowBack），
 * 固定 24dp、主题文字色，位置由调用方放在顶部左侧。
 */
@Composable
fun ComposeBackButton(onBack: () -> Unit, color: Color) {
    IconButton(onClick = onBack) {
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
