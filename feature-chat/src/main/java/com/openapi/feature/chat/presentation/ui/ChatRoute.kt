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
import com.openapi.feature.chat.presentation.LocationFixer
import com.openapi.feature.chat.presentation.LocationManagerFixer
import com.openapi.chatkit.model.ComposerAction
import com.openapi.chatkit.model.MessageAction
import com.openapi.chatkit.model.VoiceStyle
import com.openapi.speech.audio.AudioPlayer
import com.openapi.common.ui.permission.PermissionGateway
import com.openapi.common.ui.permission.PermissionOutcome
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.openapi.common.ui.permission.Permissions
import com.openapi.common.media.MAX_MESSAGE_IMAGES
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
    /** 「拍照解题」入口：目标页在 :feature-photosolve，feature 之间不许互相依赖，由上层注入（P3-22）。 */
    onOpenPhotoSolve: () -> Unit,
    /**
     * 语音通话入口（整改清单 P0-4）：通话栈在 :feature-companion，feature 之间不许
     * 互相依赖（ModuleGraphTest「用路由」），由 app 注入——语义是「跳到陪伴页并直接进通话」。
     */
    onStartVoiceCall: () -> Unit,
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
    // 点开播放的那条视频；null = 没有播放页
    var playingVideo by remember { mutableStateOf<com.openapi.chatkit.model.MessagePart.Video?>(null) }

    // 9 张是与服务端 `SendBody.attachments` 同源的硬上限（真值在 :common 的
    // [MAX_MESSAGE_IMAGES]）。`maxItems` 只是**建议**：厂商 ROM 的选择器可以不认，
    // 超了会一路漏进草稿托盘——由 [ComposerActions.addDraft] 的第二道闸挡下
    // （终评 I-1：以前本页没有这道闸，选 12 张要先把 12 张的上传流量花完再吃一个 422）。
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_MESSAGE_IMAGES),
    ) { uris ->
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
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // 只有视频选择器返回 null（取消）时什么都不做；超限/格式错误由暂存层的 Rejected 提示
        if (uri != null) viewModel.onAttachmentPicked(context, MessageKind.VIDEO, uri)
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
            // 以前用字符串类名 setClassName 启动，改包名/类名只会静默失败（整改清单 P3-22）。
            onOpenPhotoSolve()
        },
        ComposerAction("video", context.getString(R.string.chat_attach_video), Icons.Outlined.Videocam) {
            if (requireLogin(context)) videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        },
        ComposerAction("location", context.getString(R.string.chat_attach_location), Icons.Outlined.LocationOn) {
            if (requireLogin(context)) {
                val host = activity as? ComponentActivity
                if (host == null) {
                    toast(R.string.chat_location_failed)
                } else {
                    scope.launch {
                        // 先权限后定位：LocationManagerFixer 假设授权已完成
                        val outcome = PermissionGateway.of(host).request(
                            Permissions.coarseLocation(context.getString(R.string.chat_location_rationale)),
                        )
                        when (outcome) {
                            PermissionOutcome.Granted -> {
                                val fix = LocationManagerFixer(context).once(LOCATION_TIMEOUT_MS)
                                if (fix != null) viewModel.onLocationFixReady(fix.latitude, fix.longitude, fix.accuracyMeters)
                                else toast(R.string.chat_location_failed)
                            }
                            is PermissionOutcome.Denied -> toast(R.string.chat_location_denied)
                            is PermissionOutcome.PermanentlyDenied -> PermissionGateway.goToSettings(host)
                        }
                    }
                }
            }
        },
        ComposerAction("phone", context.getString(R.string.chat_attach_phone), Icons.Outlined.Call) {
            // 以前只弹「暂不可用」；通话栈已在 :feature-companion 跑通，由上层跳陪伴页直接进通话（P0-4）
            onStartVoiceCall()
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
        Box(Modifier.fillMaxSize()) {
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
            onRemoveDraft = { id ->
                // 位置 chip 不是 DraftAttachment（状态里是单值字段），按固定 id 分流
                if (id == LOCATION_DRAFT_ID) viewModel.onRemoveLocationDraft() else viewModel.onRemoveDraft(id)
            },
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
                    // 点失败的图片：组件已重载，这里给远程图重新签 URL（整改清单 P1-10）
                    is MessageAction.RetryImage -> viewModel.onRetryImage(message.id, action.index)
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
                    // 视频：本机文件在就播本机文件，只有服务端对象时点开这一刻才换签名 URL
                    is MessageAction.OpenVideo -> playingVideo = action.video
                    is MessageAction.OpenLocation ->
                        ChatPlatform.openMap(context, action.location.latitude, action.location.longitude)
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
            onCallClick = onStartVoiceCall,
            onSessionsVisibleChange = viewModel::onSessionsVisibleChange,
            onSessionSelected = viewModel::onSessionSelected,
            onCreateSession = viewModel::onCreateSession,
            onDeleteSession = viewModel::onDeleteSession,
            onDeleteMessages = viewModel::onDeleteMessages,
            onClearConversation = viewModel::onClearConversation,
            onRenameSession = viewModel::onRenameSession,
            onLoadOlder = viewModel::onLoadOlderMessages,
            onMenuClick = onMenuClick,
            onDismissError = viewModel::dismissError,
            onReload = viewModel::refresh,
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
                                    editing.second, edits, context.cacheDir, context.assets,
                                )
                            }
                            // 导出失败（渲染或写盘）时**不覆盖暂存**：草稿里还是原图，
                            // 用户至少能发出未编辑的版本。这里必须说清楚，否则就是
                            // 「以为裁过了」——和静默回退原图是同一种数据损失，只是换了个位置。
                            if (exported == null) {
                                toast(R.string.chat_image_edit_fallback)
                            } else {
                                viewModel.onReplaceDraftImage(target, exported, context)
                            }
                        }
                        pendingEdit = null
                    },
                )
            }
        }

        // 视频播放：全屏盖在对话上。本机文件在播本机文件；只剩服务端对象的点开时现换 URL。
        val video = playingVideo
        if (video != null) {
            if (video.path.isNotBlank() && java.io.File(video.path).exists()) {
                ChatVideoPlayer(video = video, onDismiss = { playingVideo = null })
            } else {
                ChatRemoteVideoPlayer(
                    video = video,
                    refreshUrl = { id -> com.openapi.feature.chat.ChatDependencies.require().refreshImageUrl(id) },
                    onDismiss = { playingVideo = null },
                )
            }
        }
        }
    }
}

/** 单次粗定位的等待上限：网络定位通常在 10 秒内出结果，再久用户已经放弃这一条了。 */
private const val LOCATION_TIMEOUT_MS = 15_000L

/** 附件上传需要登录（与旧版一致）：未登录时由 LoginGate 提示并引导登录，返回 false。 */
private fun requireLogin(context: android.content.Context): Boolean =
    com.openapi.feature.chat.ChatDependencies.loginGate().ensureLoggedIn(context)
