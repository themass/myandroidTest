package com.openapi.chat.presentation.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openapi.chat.domain.model.MessageKind
import com.openapi.chat.presentation.ChatViewModel
import com.openapi.core.media.AudioPlayer
import com.openapi.core.permission.PermissionRequester
import com.openapi.core.permission.PermissionStates
import com.openapi.core.permission.Permissions
import com.openapi.ks.myapp.data.UserLoginUtil
import com.openapi.ks.myapp.ui.user.LoginActivity

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
    val activity = LocalActivity.current
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
                if (PermissionStates.isGranted(context, Manifest.permission.RECORD_AUDIO)) {
                    viewModel.onVoiceRecordStart(context)
                } else {
                    // 首次授权的这一次按压不录音：和老 RecordButton 的行为一致，下次按下才开始录。
                    // 永久拒绝后由 PermissionRequester 引导去系统设置，而不是静默失败。
                    (activity as? ComponentActivity)?.let { host ->
                        PermissionRequester.with(host)
                            .permissions(*Permissions.RECORD_AUDIO)
                            .rationale("发送语音消息需要使用麦克风")
                            .request(object : PermissionRequester.Callback() {
                                override fun onGranted() = Unit
                            })
                    }
                }
            },
            onVoiceRecordStop = viewModel::onVoiceRecordStop,
            onVoiceRecordCancel = viewModel::onVoiceRecordCancel,
            onSpeakMessage = viewModel::onSpeakMessage,
            onCallClick = {
                android.widget.Toast.makeText(context, "语音通话即将上线", android.widget.Toast.LENGTH_SHORT).show()
            },
            onPickImage = {
                if (requireLogin(context)) {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            },
            onPickFile = {
                if (requireLogin(context)) filePicker.launch(arrayOf("*/*"))
            },
            modifier = modifier.safeDrawingPadding(),
        )
    }
}

/** 附件上传需要登录（与旧版一致）：未登录时提示并跳到登录页，返回 false。 */
private fun requireLogin(context: android.content.Context): Boolean {
    if (UserLoginUtil.getUserCache() != null) return true
    android.widget.Toast.makeText(context, com.openapi.ks.chat.R.string.need_login, android.widget.Toast.LENGTH_SHORT).show()
    context.startActivity(android.content.Intent(context, LoginActivity::class.java))
    return false
}
