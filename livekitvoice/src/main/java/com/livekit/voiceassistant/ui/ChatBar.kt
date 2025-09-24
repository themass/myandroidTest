package com.livekit.voiceassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.livekit.voiceassistant.ui.theme.Blue500

@Composable
fun ChatBar(
    value: String,
    onValueChange: (String) -> Unit,
    onChatSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sendButtonColors = ButtonDefaults.buttonColors(
        containerColor = Blue500,
        contentColor = Color.White
    )
    ConstraintLayout(
        modifier = Modifier
            .imePadding()
            .sizeIn(minHeight = 48.dp)
            .clip(RoundedCornerShape(with(LocalDensity.current) { 24.dp.toPx() }))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
            .then(modifier)
    ) {
        val (sendButton, messageInput) = createRefs()

        LKTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
            colors = TextFieldDefaults.colors().copy(
                disabledTextColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            maxLines = 3,
            placeholder = {
                Text("Message")
            },
            modifier = Modifier
                .constrainAs(messageInput) {
                    start.linkTo(parent.start, 8.dp)
                    end.linkTo(sendButton.start, 8.dp)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    width = Dimension.fillToConstraints
                    height = Dimension.wrapContent
                },
        )

        Button(
            colors = sendButtonColors,
            shape = RoundedCornerShape(50),
            onClick = {
                onChatSend(value)
            },
            enabled = value.isNotEmpty(),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .padding(0.dp)
                .constrainAs(sendButton) {
                    end.linkTo(parent.end)
                    bottom.linkTo(parent.bottom)
                    width = Dimension.ratio("1:1")
                    height = Dimension.preferredValue(32.dp)
                }
        ) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "Send Message", tint = if(value.isEmpty()) MaterialTheme.colorScheme.onSurface else Color.White)
        }
    }

}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5, name = "空输入状态")
@Composable
fun ChatBarEmptyPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        ChatBar(
            value = "",
            onValueChange = { },
            onChatSend = { },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5, name = "有内容状态")
@Composable
fun ChatBarWithContentPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        ChatBar(
            value = "这是一条测试消息",
            onValueChange = { },
            onChatSend = { },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5, name = "长文本状态")
@Composable
fun ChatBarLongTextPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        ChatBar(
            value = "这是一条很长的测试消息，用来测试多行文本的显示效果，看看输入框如何处理换行和高度调整。",
            onValueChange = { },
            onChatSend = { },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "深色主题")
@Composable
fun ChatBarDarkThemePreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        ChatBar(
            value = "深色主题测试",
            onValueChange = { },
            onChatSend = { },
            modifier = Modifier.fillMaxWidth()
        )
    }
}