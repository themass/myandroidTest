package com.openapi.chat.presentation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openapi.chat.domain.model.MessageKind
import com.openapi.chat.presentation.ChatViewModel
import androidx.activity.result.PickVisualMediaRequest
import com.openapi.core.media.AudioPlayer

/**
 * 对话界面的完整装配：ViewModel + 语音播放 + 麦克风权限。
 *
 * 抽出来是为了让 [com.openapi.chat.ChatActivity]（独立页面）和主页的对话 Tab
 * 共用同一份实现，避免两处各写一套互不同步的代码。
 */
@Composable
fun ChatRoute(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    // 首次授权的这一次按压不录音：和老 RecordButton 的行为一致，下次按下才开始录
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onAttachmentPicked(context, MessageKind.IMAGE, it) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onAttachmentPicked(context, MessageKind.FILE, it) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        ChatScreen(
            state = state,
            onInputChange = viewModel::onInputChange,
            onSend = viewModel::onSend,
            onCharacterSelected = viewModel::onCharacterSelected,
            onCreateCharacter = viewModel::onCreateCharacter,
            onUpdateCharacter = viewModel::onUpdateCharacter,
            onDeleteCharacter = viewModel::onDeleteCharacter,
            onVoiceClick = { message ->
                message.voice?.let { voice -> AudioPlayer.toggle(message.id, voice.uri) }
            },
            onVoiceRecordStart = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) {
                    viewModel.onVoiceRecordStart(context)
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onVoiceRecordStop = viewModel::onVoiceRecordStop,
            onVoiceRecordCancel = viewModel::onVoiceRecordCancel,
            onCallClick = {
                android.widget.Toast.makeText(context, "语音通话即将上线", android.widget.Toast.LENGTH_SHORT).show()
            },
            onPickImage = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onPickFile = { filePicker.launch(arrayOf("*/*")) },
            modifier = modifier.safeDrawingPadding(),
        )
    }
}
