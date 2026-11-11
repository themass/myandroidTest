@file:OptIn(ExperimentalFoundationApi::class)

package com.openapi.chatkit.input

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.chatkit.R
import com.openapi.chatkit.model.ComposerAction
import com.openapi.chatkit.model.ComposerState
import com.openapi.chatkit.model.VoiceStyle
import com.openapi.chatkit.theme.ChatKit

internal val CardShape = RoundedCornerShape(16.dp)
internal val IconInk = Color(0xFF1F1F1F)
internal val SendBlue = Color(0xFF0066FF)
internal val SendBlueDisabled = Color(0xFFB3CCFF)

/** 白色圆角卡片：16dp 圆角、柔和阴影。 */
@Composable
internal fun InputCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = CardShape, ambientColor = Color(0x80000000), spotColor = Color(0x80000000))
            .background(Color.White, CardShape),
        content = content,
    )
}

/** 卡片里的图标位：44×56dp 点击区，图标居中。 */
@Composable
internal fun IconSlot(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 56.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 键盘图标：圆圈里一个键盘（对应豆包的圆形线框图标）。 */
@Composable
internal fun KeyboardGlyph() {
    Box(
        Modifier
            .size(26.dp)
            .border(1.5.dp, IconInk, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Keyboard,
            contentDescription = stringResource(R.string.chatkit_keyboard),
            tint = IconInk,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** 文字输入框：卡片内，17sp，最多 5 行，随内容向上长高。 */
@Composable
internal fun InputField(
    text: String,
    onTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChatKit.colors
    Box(
        modifier.heightIn(min = 56.dp).padding(start = 12.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
            textStyle = TextStyle(fontSize = 17.sp, color = Color.Black),
            cursorBrush = SolidColor(colors.accent),
            maxLines = 5,
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(stringResource(R.string.chatkit_input_hint), fontSize = 17.sp, color = Color(0xFF9CA3AF))
                }
                inner()
            },
        )
    }
}

@Composable
internal fun Hint(text: String, emphasized: Boolean) {
    val colors = ChatKit.colors
    Surface(color = if (emphasized) colors.accent.copy(alpha = 0.08f) else colors.surface) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            textAlign = if (emphasized) TextAlign.Center else TextAlign.Start,
            fontSize = if (emphasized) ChatKit.typography.caption else ChatKit.typography.body,
            color = if (emphasized) colors.accent else colors.bubbleText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
