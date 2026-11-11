package com.openapi.feature.chat.presentation.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.openapi.feature.chat.data.ChatMessageActions
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.presentation.ChatViewModel
import com.openapi.chatkit.model.ComposerAction
import com.openapi.chatkit.model.MessageAction
import com.openapi.chatkit.model.VoiceStyle
import com.openapi.speech.audio.AudioPlayer
import com.openapi.common.ui.permission.PermissionGateway
import com.openapi.common.ui.permission.PermissionOutcome
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.openapi.common.ui.permission.Permissions
import com.openapi.feature.chat.R

/**
 * 对话界面的完整装配：ViewModel、权限、选择器、平台能力（复制、分享、打开文件）。
 *
 * [ChatActivity] 和主页的对话 Tab 共用这一份实现。
 */
@Composable
fun ChatRoute(
    modifier: Modifier = Modifier,
    onMenuClick: (() -> Unit)? = null,
    viewModel: ChatViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    var voiceStyle by remember { mutableStateOf(ChatPlatform.loadVoiceStyle(context)) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    // 选中/拍下的图片先暂存，再进入编辑；取消编辑时丢弃，不进入草稿
    var pendingEdit by remember { mutableStateOf<Pair<java.io.File, android.graphics.Bitmap>?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        // 只对第一张进入编辑：多选时其余图片照常加入草稿，避免一次弹多个编辑器
        if (uris.isNotEmpty()) {
            viewModel.onImagesPicked(context, listOf(uris.first()), openEditor = { file, bitmap ->
                pendingEdit = file to bitmap
            })
            if (uris.size > 1) viewModel.onImagesPicked(context, uris.drop(1))
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onAttachmentPicked(context, MessageKind.FILE, it) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) {
            viewModel.onImagesPicked(context, listOf(uri), openEditor = { file, bitmap ->
                pendingEdit = file to bitmap
            })
        }
    }

    fun toast(res: Int) = Toast.makeText(context, context.getString(res), Toast.LENGTH_SHORT).show()

    // 「+」面板入口：与旧版 InputRecordView 的入口对齐，另加「拍摄」
    val composerActions = listOf(
        ComposerAction("album", context.getString(R.string.chat_attach_album), Icons.Outlined.PhotoLibrary) {
            if (requireLogin(context)) imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        ComposerAction("camera", context.getString(R.string.chat_attach_camera), Icons.Outlined.CameraAlt) {
            if (requireLogin(context)) {
                // 清单里声明了相机权限，就必须先在运行时授予，否则系统拒绝拉起相机并让 App 崩溃
                val launchCamera = {
                    val uri = ChatPlatform.newCameraUri(context)
                    cameraUri = uri
                    camera.launch(uri)
                }
                if (PermissionGateway.isGranted(context, Permissions.CAMERA)) {
                    launchCamera()
                } else {
                    (activity as? ComponentActivity)?.let { host ->
                        scope.launch {
                            val outcome = PermissionGateway.of(host).request(
                                Permissions.camera(context.getString(R.string.chat_camera_rationale)),
                            )
                            when (outcome) {
                                PermissionOutcome.Granted -> launchCamera()
                                is PermissionOutcome.Denied -> toast(R.string.chat_camera_denied)
                                is PermissionOutcome.PermanentlyDenied -> PermissionGateway.goToSettings(host)
                            }
                        }
                    }
                }
            }
        },
        ComposerAction("file", context.getString(R.string.chat_attach_file), Icons.Outlined.Folder) {
            if (requireLogin(context)) filePicker.launch(arrayOf("*/*"))
        },
        ComposerAction("photo_solve", context.getString(R.string.chat_attach_photo_solve), Icons.Outlined.Calculate) {
            // 拍照解题在 :feature-photosolve，这里用显式组件名启动，避免 feature 之间互相依赖。
            // TODO: photosolve 拆成独立模块后改为直接调用其入口。
            context.startActivity(
                android.content.Intent().setClassName(context.packageName, "com.openapi.feature.photosolve.PhotoSolveActivity"),
            )
        },
        ComposerAction("video", context.getString(R.string.chat_attach_video), Icons.Outlined.Videocam) {
            toast(R.string.chat_video_soon)
        },
        ComposerAction("location", context.getString(R.string.chat_attach_location), Icons.Outlined.LocationOn) {
            toast(R.string.chat_location_soon)
        },
        ComposerAction("phone", context.getString(R.string.chat_attach_phone), Icons.Outlined.Call) {
            toast(R.string.talktome_unavailable)
        },
        ComposerAction("help", context.getString(R.string.chat_attach_help), Icons.AutoMirrored.Outlined.HelpOutline) {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(com.openapi.core.config.Constants.helpUrl),
                ),
            )
        },
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ChatScreen(
            state = state,
            voiceStyle = voiceStyle,
            composerActions = composerActions,
            onVoiceStyleChange = { style ->
                voiceStyle = style
                ChatPlatform.saveVoiceStyle(context, style)
            },
            onInputChange = viewModel::onInputChange,
            onSend = viewModel::onSend,
            onStop = viewModel::onStopGenerating,
            onRemoveDraft = viewModel::onRemoveDraft,
            onCancelEdit = viewModel::onCancelEdit,
            onVoiceDraftSendText = viewModel::onVoiceDraftSendText,
            onVoiceDraftSendAudio = viewModel::onVoiceDraftSendAudio,
            onVoiceDraftCancel = viewModel::onVoiceDraftCancel,
            onVoiceRecordStart = {
                if (PermissionGateway.isGranted(context, Permissions.RECORD_AUDIO)) {
                    viewModel.onVoiceRecordStart(context, liveTranscript = voiceStyle == VoiceStyle.Ripple || voiceStyle == VoiceStyle.Floating)
                } else {
                    // 首次授权的这一次按压不录音：和老 RecordButton 的行为一致，下次按下才开始录。
                    (activity as? ComponentActivity)?.let { host ->
                        scope.launch {
                            val outcome = PermissionGateway.of(host).request(
                                Permissions.recordAudio(context.getString(R.string.chat_voice_rationale)),
                            )
                            when (outcome) {
                                PermissionOutcome.Granted -> Unit
                                is PermissionOutcome.Denied -> toast(R.string.chat_voice_denied)
                                is PermissionOutcome.PermanentlyDenied -> PermissionGateway.goToSettings(host)
                            }
                        }
                    }
                }
            },
            onVoiceRecordStop = viewModel::onVoiceRecordStop,
            onVoiceRecordCancel = viewModel::onVoiceRecordCancel,
            onVoiceRecordToText = viewModel::onVoiceRecordStopToText,
            onMessageAction = { message, action ->
                when (action) {
                    MessageAction.Copy -> {
                        val text = message.text
                        if (text.isNotBlank()) ChatPlatform.copy(context, text)
                    }
                    MessageAction.Delete -> viewModel.onDeleteMessage(message.id)
                    // 多选由列表自己处理（见 ChatMessageList），不会到这里
                    MessageAction.Select -> Unit
                    MessageAction.Retry -> viewModel.onRetry(message.id)
                    MessageAction.Edit -> viewModel.onEditMessage(message.id)
                    MessageAction.Speak -> viewModel.onSpeakMessage(context, message)
                    MessageAction.Like -> {
                        viewModel.onToggleLike(message.id)
                        // 本地收藏是切换语义：每次点击都调用一次，和界面状态保持一致
                        ChatMessageActions.toggleLike(context, message.id)
                    }
                    MessageAction.Dislike -> Unit // 由 ChatScreen 弹反馈框，提交后走 onFeedback
                    MessageAction.Share -> if (message.text.isNotBlank()) ChatPlatform.share(context, message.text)
                    MessageAction.Regenerate -> viewModel.onRegenerate(message.id)
                    MessageAction.PlayVoice -> message.voice?.let { AudioPlayer.toggle(message.id, it.uri) }
                    is MessageAction.OpenFile -> ChatPlatform.openFile(context, action.file.path, action.file.name)
                }
            },
            onFeedback = { message, text ->
                ChatMessageActions.sendFeedback(context, message.id, text)
                viewModel.onDisliked(message.id)
            },
            onCharacterSelected = viewModel::onCharacterSelected,
            onCreateCharacter = viewModel::onCreateCharacter,
            onUpdateCharacter = viewModel::onUpdateCharacter,
            onDeleteCharacter = viewModel::onDeleteCharacter,
            onRoleSelected = viewModel::onRoleSelected,
            onRolePickerVisibleChange = viewModel::onRolePickerVisibleChange,
            onCallClick = { toast(R.string.chat_call_soon) },
            onSessionsVisibleChange = viewModel::onSessionsVisibleChange,
            onSessionSelected = viewModel::onSessionSelected,
            onCreateSession = viewModel::onCreateSession,
            onDeleteSession = viewModel::onDeleteSession,
            onDeleteMessages = viewModel::onDeleteMessages,
            onClearConversation = viewModel::onClearConversation,
            onRenameSession = viewModel::onRenameSession,
            onLoadOlder = viewModel::onLoadOlderMessages,
            onMenuClick = onMenuClick,
            // 顶部不处理：顶栏（RoleHeader）用品牌色画到状态栏底下，自己用 statusBarsPadding 避开状态栏图标
            modifier = modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            ),
        )

        // 待发送图片的本地编辑：编辑结果覆盖暂存文件，UI 走同一张草稿
        val editing = pendingEdit
        if (editing != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                com.openapi.mediaview.edit.PhotoEditor(
                    bitmap = editing.second,
                    onCancel = { pendingEdit = null },
                    onDone = { edits ->
                        val target = editing.first
                        scope.launch {
                            val exported = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.openapi.mediaview.edit.PhotoEditExporter.export(
                                    editing.second, edits, context.cacheDir,
                                )
                            }
                            if (exported != null) viewModel.onReplaceDraftImage(target, exported, context)
                        }
                        pendingEdit = null
                    },
                )
            }
        }
    }
}

/** 附件上传需要登录（与旧版一致）：未登录时由 LoginGate 提示并引导登录，返回 false。 */
private fun requireLogin(context: android.content.Context): Boolean =
    com.openapi.feature.chat.ChatDependencies.loginGate().ensureLoggedIn(context)
