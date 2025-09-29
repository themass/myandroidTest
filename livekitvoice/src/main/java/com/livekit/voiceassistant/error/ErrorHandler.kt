package com.livekit.voiceassistant.error

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livekit.voiceassistant.config.AppConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 错误类型枚举
 */
enum class ErrorType {
    CONNECTION_ERROR,
    AUDIO_ERROR,
    MESSAGE_ERROR,
    PERMISSION_ERROR,
    UNKNOWN_ERROR
}

/**
 * 错误信息数据类
 */
data class AppError(
    val type: ErrorType,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRetryable: Boolean = false
)

/**
 * 全局错误处理器
 */
object GlobalErrorHandler {
    private val _errors = mutableStateListOf<AppError>()
    val errors: List<AppError> = _errors
    
    /**
     * 添加错误
     */
    fun addError(error: AppError) {
        _errors.add(error)
        if (AppConfig.ENABLE_DEBUG_LOGGING) {
            println("❌ [${AppConfig.LOG_TAG_ERROR}] ${error.type}: ${error.message}")
        }
        
        // 自动清除错误
        if (error.type != ErrorType.PERMISSION_ERROR) {
            kotlinx.coroutines.GlobalScope.launch {
                delay(AppConfig.ERROR_DISPLAY_DURATION.toLong())
                removeError(error)
            }
        }
    }
    
    /**
     * 移除错误
     */
    fun removeError(error: AppError) {
        _errors.remove(error)
    }
    
    /**
     * 清除所有错误
     */
    fun clearAllErrors() {
        _errors.clear()
    }
    
    /**
     * 创建连接错误
     */
    fun createConnectionError(message: String, isRetryable: Boolean = true): AppError {
        return AppError(
            type = ErrorType.CONNECTION_ERROR,
            message = "连接错误: $message",
            isRetryable = isRetryable
        )
    }
    
    /**
     * 创建音频错误
     */
    fun createAudioError(message: String): AppError {
        return AppError(
            type = ErrorType.AUDIO_ERROR,
            message = "音频错误: $message"
        )
    }
    
    /**
     * 创建消息错误
     */
    fun createMessageError(message: String): AppError {
        return AppError(
            type = ErrorType.MESSAGE_ERROR,
            message = "消息错误: $message"
        )
    }
    
    /**
     * 创建权限错误
     */
    fun createPermissionError(message: String): AppError {
        return AppError(
            type = ErrorType.PERMISSION_ERROR,
            message = "权限错误: $message"
        )
    }
    
    /**
     * 创建未知错误
     */
    fun createUnknownError(message: String): AppError {
        return AppError(
            type = ErrorType.UNKNOWN_ERROR,
            message = "未知错误: $message"
        )
    }
}

/**
 * 错误显示组件
 */
@Composable
fun ErrorDisplay(
    modifier: Modifier = Modifier
) {
    val errors = GlobalErrorHandler.errors
    
    if (errors.isNotEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            errors.forEach { error ->
                ErrorCard(
                    error = error,
                    onDismiss = { GlobalErrorHandler.removeError(error) }
                )
            }
        }
    }
}

/**
 * 错误卡片组件
 */
@Composable
private fun ErrorCard(
    error: AppError,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (error.type) {
                ErrorType.CONNECTION_ERROR -> Color(0xFFFF6B6B).copy(alpha = 0.9f)
                ErrorType.AUDIO_ERROR -> Color(0xFFFFA500).copy(alpha = 0.9f)
                ErrorType.MESSAGE_ERROR -> Color(0xFF9B59B6).copy(alpha = 0.9f)
                ErrorType.PERMISSION_ERROR -> Color(0xFFE74C3C).copy(alpha = 0.9f)
                ErrorType.UNKNOWN_ERROR -> Color(0xFF95A5A6).copy(alpha = 0.9f)
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = error.message,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            
            if (error.type != ErrorType.PERMISSION_ERROR) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("关闭", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * 错误处理扩展函数
 */
fun Throwable.toAppError(): AppError {
    return when {
        message?.contains("connection", ignoreCase = true) == true -> 
            GlobalErrorHandler.createConnectionError(message ?: "连接失败")
        message?.contains("audio", ignoreCase = true) == true -> 
            GlobalErrorHandler.createAudioError(message ?: "音频错误")
        message?.contains("permission", ignoreCase = true) == true -> 
            GlobalErrorHandler.createPermissionError(message ?: "权限不足")
        else -> 
            GlobalErrorHandler.createUnknownError(message ?: "未知错误")
    }
}
