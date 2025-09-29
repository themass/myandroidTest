package com.livekit.voiceassistant.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.provider.MediaStore
import android.net.Uri
import com.livekit.voiceassistant.R

/**
 * 消息类型枚举
 */
enum class MessageType {
    TEXT,    // 文本消息
    IMAGE,   // 图片消息  
    AUDIO,   // 音频消息
    VIDEO,   // 视频消息
    FILE     // 文件消息
}

/**
 * 消息选择器选项
 */
data class MessageOption(
    val type: MessageType,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String = ""
)

/**
 * 重构的简化输入框组件
 * 支持多种格式消息：文字、图片、音频、视频、文件
 */
@Composable
fun KimiRecordInputView(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "输入消息...",
    showKeyboardButton: Boolean = true, // 是否显示Keyboard按钮
    onKeyboardClick: (() -> Unit)? = null, // Keyboard按钮点击回调，null表示不可点击
    onImageSelected: ((Uri) -> Unit)? = null, // 🆕 图片选择回调，返回Uri
    onAudioSelected: ((Uri) -> Unit)? = null, // 🆕 音频选择回调，返回Uri
    onVideoSelected: ((Uri) -> Unit)? = null, // 🆕 视频选择回调，返回Uri
    onFileSelected: ((Uri) -> Unit)? = null   // 🆕 文件选择回调，返回Uri
) {
    // 消息选择器显示状态
    var showMessageSelector by remember { mutableStateOf(false) }
    
    // 使用媒体选择器辅助函数
    val mediaPickers = rememberMediaPickers(
        onImageSelected = { uri -> onImageSelected?.invoke(uri) },
        onVideoSelected = { uri -> onVideoSelected?.invoke(uri) },
        onAudioSelected = { uri -> onAudioSelected?.invoke(uri) },
        onFileSelected = { uri -> onFileSelected?.invoke(uri) },
        onCameraResult = { uri -> onImageSelected?.invoke(uri) } // 拍照结果作为图片处理
    )
    // 简化的输入框布局
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight() // 使用wrapContentHeight自适应高度
            .padding(horizontal = 8.dp, vertical = 4.dp), // 减少垂直padding
        verticalAlignment = Alignment.CenterVertically, // 改为垂直居中对齐
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 左侧Keyboard按钮（根据参数控制显示和点击）
        if (showKeyboardButton) {
            if (onKeyboardClick != null) {
                // 可点击的按钮
                IconButton(
                    onClick = onKeyboardClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = colorResource(id = R.color.kimi_old_purple_primary),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "键盘切换",
                        tint = colorResource(id = R.color.kimi_white_full),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                // 仅显示，不可点击
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = colorResource(id = R.color.kimi_old_purple_secondary),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "键盘输入",
                        tint = colorResource(id = R.color.kimi_white_full),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            // 不显示按钮，用Spacer占位保持布局一致
            Spacer(modifier = Modifier.size(2.dp))
        }

        // 中间输入框
        Box(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 40.dp) // 减少最小高度到40dp
                .background(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(18.dp) // 圆角设计
                )
                .padding(horizontal = 12.dp, vertical = 6.dp) // 减少垂直padding
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = TextStyle(
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(), // 自适应高度
                maxLines = 4, // 最多4行
                minLines = 1, // 最少1行
                singleLine = false,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(), // 改为自适应高度，不填充
                        contentAlignment = Alignment.CenterStart // 垂直和水平居中对齐
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = Color.Gray,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        // 右侧发送按钮 - 暂时统一显示发送图标
        IconButton(
            onClick = {
                if (text.isNotEmpty()) {
                    // 有文字时执行发送
                    onSend()
                } else {
                    // 无文字时暂时不执行任何操作，后续优化
                    // showMessageSelector = true // 暂时注释掉消息选择器
                }
            },
            modifier = Modifier
                .size(40.dp) // 调整按钮大小与输入框高度匹配
                .background(
                    color = if (text.isNotEmpty()) 
                        colorResource(id = R.color.kimi_old_purple_primary)
                    else 
                        colorResource(id = R.color.kimi_old_purple_secondary),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send, // 暂时统一显示发送图标
                contentDescription = "发送",
                tint = colorResource(id = R.color.kimi_white_full),
                modifier = Modifier.size(20.dp) // 稍微增大图标
            )
        }
        
        // 消息选择器弹窗
        if (showMessageSelector) {
            KimiMessageSelector(
                onDismiss = { showMessageSelector = false },
                onImageSelect = { 
                    // 从相册选择图片
                    mediaPickers.selectImage()
                    showMessageSelector = false
                },
                onAudioSelect = { 
                    // 选择音频文件或录音
                    mediaPickers.recordAudio()
                    showMessageSelector = false
                },
                onVideoSelect = { 
                    // 从相册选择视频
                    mediaPickers.selectVideo()
                    showMessageSelector = false
                },
                onFileSelect = { 
                    // 选择文件
                    mediaPickers.selectFile()
                    showMessageSelector = false
                },
                onCameraSelect = {
                    // 拍照
                    mediaPickers.takePhoto()
                    showMessageSelector = false
                },
                onVideoRecord = {
                    // 录制视频
                    mediaPickers.recordVideo()
                    showMessageSelector = false
                }
            )
        }
    }
}

/**
 * 消息选择器组件 - 仿微信样式
 */
@Composable
private fun KimiMessageSelector(
    onDismiss: () -> Unit,
    onImageSelect: () -> Unit,
    onAudioSelect: () -> Unit,
    onVideoSelect: () -> Unit,
    onFileSelect: () -> Unit,
    onCameraSelect: () -> Unit = {},
    onVideoRecord: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 消息选择选项
    val messageOptions = listOf(
        MessageOption(MessageType.IMAGE, "相册", Icons.Default.PhotoLibrary, "选择图片"),
        MessageOption(MessageType.VIDEO, "拍摄", Icons.Default.Videocam, "录制视频"),
        MessageOption(MessageType.VIDEO, "视频通话", Icons.Default.VideoCall, "发起视频通话"),
        MessageOption(MessageType.FILE, "位置", Icons.Default.LocationOn, "发送位置"),
        MessageOption(MessageType.FILE, "红包", Icons.Default.CardGiftcard, "发送红包"),
        MessageOption(MessageType.FILE, "礼物", Icons.Default.Redeem, "发送礼物"),
        MessageOption(MessageType.FILE, "转账", Icons.Default.SwapHoriz, "转账功能"),
        MessageOption(MessageType.AUDIO, "语音输入", Icons.Default.Mic, "录制语音")
    )

    // 半透明遮罩 + 底部弹出选择器
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 半透明遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable { onDismiss() }
        )
        
        // 底部消息选择器
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 标题
                Text(
                    text = "选择消息类型",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 选项网格 (2x4)
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 第一行：相册、拍摄、视频通话、位置
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        messageOptions.take(4).forEach { option ->
                            MessageOptionItem(
                                option = option,
                                onClick = {
                                    when (option.title) {
                                        "相册" -> onImageSelect()        // 从相册选择图片
                                        "拍摄" -> onVideoRecord()       // 录制视频
                                        "视频通话" -> onVideoSelect()   // 选择视频文件
                                        "位置" -> onFileSelect()        // 位置功能
                                        else -> onFileSelect()
                                    }
                                }
                            )
                        }
                    }
                    
                    // 第二行：红包、礼物、转账、语音输入
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        messageOptions.drop(4).forEach { option ->
                            MessageOptionItem(
                                option = option,
                                onClick = {
                                    when (option.title) {
                                        "红包" -> onFileSelect()        // 红包功能
                                        "礼物" -> onFileSelect()        // 礼物功能
                                        "转账" -> onFileSelect()        // 转账功能
                                        "语音输入" -> onAudioSelect()   // 录音功能
                                        else -> onFileSelect()
                                    }
                                }
                            )
                        }
                    }
                }
                
                // 取消按钮
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = "取消",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * 消息选项项目组件
 */
@Composable
private fun MessageOptionItem(
    option: MessageOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 图标背景
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = Color(0xFFF5F5F5),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.title,
                tint = Color(0xFF666666),
                modifier = Modifier.size(24.dp)
            )
        }
        
        // 标题
        Text(
            text = option.title,
            fontSize = 12.sp,
            color = Color.Black,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// 预览
@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "输入框预览")
@Composable
fun KimiRecordInputViewPreview() {
    var text by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(id = R.color.kimi_old_bg_middle))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 默认状态（显示按钮，不可点击）
        KimiRecordInputView(
            text = "",
            onTextChange = { },
            onSend = { },
            placeholder = "默认状态 - 显示按钮不可点击"
        )
        
        // 可点击按钮状态
        KimiRecordInputView(
            text = "",
            onTextChange = { },
            onSend = { },
            showKeyboardButton = true,
            onKeyboardClick = { /* 按钮点击逻辑 */ },
            placeholder = "可点击按钮状态"
        )
        
        // 隐藏按钮状态
        KimiRecordInputView(
            text = "",
            onTextChange = { },
            onSend = { },
            showKeyboardButton = false,
            placeholder = "隐藏按钮状态"
        )
        
        // 有内容状态
        KimiRecordInputView(
            text = "测试消息",
            onTextChange = { },
            onSend = { },
            showKeyboardButton = true,
            onKeyboardClick = { /* 点击处理 */ }
        )
        
        // 多媒体消息支持预览
        KimiRecordInputView(
            text = text,
            onTextChange = { text = it },
            onSend = { text = "" },
            showKeyboardButton = false,
            onImageSelected = { text = "选择了图片" },
            onAudioSelected = { text = "选择了音频" },
            onVideoSelected = { text = "选择了视频" },
            onFileSelected = { text = "选择了文件" },
            placeholder = "支持多媒体消息 - 点击+号试试..."
        )
    }
}