package com.livekit.voiceassistant.component

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Kimi媒体选择器工具类
 * 参考InputRecordView和PictureFileUtil的实现
 */
object KimiMediaPicker {
    
    /**
     * 创建图片Uri用于拍照
     */
    fun createImageUri(context: Context): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = context.getExternalFilesDir("Pictures")
        val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }
}

/**
 * 媒体选择器Composable辅助函数
 * 简化版本，专注于基本功能
 */
@Composable
fun rememberMediaPickers(
    onImageSelected: (Uri) -> Unit = {},
    onVideoSelected: (Uri) -> Unit = {},
    onAudioSelected: (Uri) -> Unit = {},
    onFileSelected: (Uri) -> Unit = {},
    onCameraResult: (Uri) -> Unit = {}
): MediaPickersState {
    val context = LocalContext.current
    
    // 图片选择器 - 从相册选择
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            onImageSelected(it)
            println("从相册选择图片: $it")
        }
    }
    
    // 视频选择器 - 从相册选择
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            onVideoSelected(it)
            println("从相册选择视频: $it")
        }
    }
    
    // 音频选择器 - 从文件选择
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            onAudioSelected(it)
            println("选择音频文件: $it")
        }
    }
    
    // 文件选择器 - 通用文件
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            onFileSelected(it)
            println("选择文件: $it")
        }
    }
    
    // 拍照功能
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && cameraImageUri != null) {
            onCameraResult(cameraImageUri!!)
            println("拍照成功: $cameraImageUri")
        }
    }
    
    return MediaPickersState(
        selectImage = { imagePickerLauncher.launch("image/*") },
        selectVideo = { videoPickerLauncher.launch("video/*") },
        selectAudio = { audioPickerLauncher.launch("audio/*") },
        selectFile = { filePickerLauncher.launch("*/*") },
        takePhoto = {
            try {
                val uri = KimiMediaPicker.createImageUri(context)
                cameraImageUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                println("拍照启动失败: ${e.message}")
            }
        },
        recordVideo = {
            // 录制视频暂时使用选择视频
            videoPickerLauncher.launch("video/*")
        },
        recordAudio = {
            // 录音暂时使用选择音频文件
            audioPickerLauncher.launch("audio/*")
        }
    )
}

/**
 * 媒体选择器状态数据类
 */
data class MediaPickersState(
    val selectImage: () -> Unit,      // 从相册选择图片
    val selectVideo: () -> Unit,      // 从相册选择视频
    val selectAudio: () -> Unit,      // 选择音频文件
    val selectFile: () -> Unit,       // 选择其他文件
    val takePhoto: () -> Unit,        // 拍照
    val recordVideo: () -> Unit,      // 录制视频
    val recordAudio: () -> Unit       // 录音
)