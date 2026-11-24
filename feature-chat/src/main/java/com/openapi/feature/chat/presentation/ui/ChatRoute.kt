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
import com.openapi.chatkit.message.parts.LocalMessageImageEditHandler
import com.openapi.speech.audio.AudioPlayer
import com.openapi.common.ui.permission.PermissionGateway
import com.openapi.common.ui.permission.PermissionOutcome
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.openapi.common.ui.permission.Permissions
import com.openapi.common.media.MAX_IMAGES_PER_MESSAGE_PICK
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
    // 选/拍的图片先暂存，再进入编辑；取消编辑时丢弃，不进入草稿。
    // 两种来源分开（见 PendingEdit）：草稿图编辑完写回暂存文件；消息图编辑完作为**新草稿**加入输入框。
    var pendingEdit by remember { mutableStateOf<PendingEdit?>(null) }
    // 点开播放的那条视频；null = 没有播放页
    var playingVideo by remember { mutableStateOf<com.openapi.chatkit.model.MessagePart.Video?>(null) }

    // 选择器一次最多 4 张：这是**产品上限**（[MAX_IMAGES_PER_MESSAGE_PICK]），不是服务端那条
    // 9 张的传输硬上限（[com.openapi.common.media.MAX_MESSAGE_IMAGES]，改它要两端同步）——
    // 4 ≤ 9，客户端永远够不到 422。`maxItems` 只是**建议**：厂商 ROM 的选择器可以不认，
    // 超了会一路漏进草稿托盘——由 [ComposerActions.addDraft] 的第二道闸（同一个产品上限）挡下。
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_IMAGES_PER_MESSAGE_PICK),
    ) { uris ->
        // 相册多选**完全不进编辑器**（用户要求）：全部图片直接加入草稿，
        // 逐张弹编辑器在多选场景里是灾难。要编辑走「拍照」或大图上的「编辑」入口。
        if (uris.isNotEmpty()) viewModel.onImagesPicked(context, uris)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onAttachmentPicked(context, MessageKind.FILE, it) }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // 只有视频选择器返回 null（取消）时什么都不做；超限/格式错误由暂存层的 Rejected 提示。
        // 这条选择器是单选、恒可用（没有条数上限可加），所以「满格的图片 + 1 视频」全靠
        // [ComposerActions.addDraft] 的合计闸（产品上限 4）挡下——不挡的话字节传完才吃 422（cleanup P0）。
        if (uri != null) viewModel.onAttachmentPicked(context, MessageKind.VIDEO, uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) {
            // 拍照保持进编辑器（用户要求：只有相册多选不进）。暂存文件已入草稿，编辑结果写回它。
            viewModel.onImagesPicked(context, listOf(uri), openEditor = { file, bitmap ->
                pendingEdit = PendingEdit.Draft(file, bitmap)
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
        // 消息大图上的「编辑」入口（chatkit 的 ImageViewer 只有拿到非空 handler 才画按钮）：
        // 把那张图解码后送进同一个编辑流。已发送消息没有草稿可覆盖——编辑结果**另存为新草稿附件**
        // 加入输入框（见 PendingEdit.Message 的注释），不回写历史消息。
        androidx.compose.runtime.CompositionLocalProvider(
            LocalMessageImageEditHandler provides { source ->
                scope.launch {
                    // 消息大图的来源可能是本机路径、也可能是远程地址：先落成本地文件
                    // （http 先下载到缓存，见 AttachmentActions.localImageFile），再按 EXIF 方向解码。
                    val bitmap = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        AttachmentActions.localImageFile(context, source)?.let { file ->
                            com.openapi.common.image.ImageUtils.decodeUriApplyingExifOrientation(
                                context,
                                android.net.Uri.fromFile(file),
                            )
                        }
                    }
                    if (bitmap == null) toast(R.string.chat_image_read_failed)
                    else pendingEdit = PendingEdit.Message(bitmap)
                }
            },
        ) {
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

        // 待发送/消息图片的本地编辑（两个入口、同一台编辑机器，见 PendingEdit）：
        // - 草稿（拍照进来的）：编辑结果覆盖暂存文件，UI 走同一张草稿；
        // - 消息大图上的「编辑」：编辑结果**另存为新草稿附件**加入输入框。已发送的消息没有
        //   本地覆盖文件——服务端也没有单条替换附件的接口，硬造「覆盖已发送消息」的语义会让
        //   本地显示与服务端正式版本各说各话（腐化历史），所以发不发、发给谁由用户再决定一次。
        // 导出成功后顺带存一份到本地相册（要求④），成功与失败都有 toast。
        val editing = pendingEdit
        if (editing != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                com.openapi.mediaview.edit.PhotoEditor(
                    bitmap = editing.bitmap,
                    onCancel = { pendingEdit = null },
                    onDone = { edits ->
                        scope.launch {
                            val exported = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.openapi.mediaview.edit.PhotoEditExporter.export(
                                    editing.bitmap, edits, context.cacheDir, context.assets,
                                )
                            }
                            // 导出失败（渲染或写盘）时**不覆盖暂存**：草稿里还是原图，
                            // 用户至少能发出未编辑的版本。这里必须说清楚，否则就是
                            // 「以为裁过了」——和静默回退原图是同一种数据损失，只是换了个位置。
                            if (exported == null) {
                                toast(R.string.chat_image_edit_fallback)
                            } else {
                                // 保存到相册：与草稿写回相互独立，保存失败不影响编辑结果本身。
                                // Android 9（minSdk 28）没有 scoped storage 的零权限写入口，单独给可读提示。
                                val savedName = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    AttachmentActions.saveEditedImageToGallery(context, exported)
                                }
                                when {
                                    savedName != null -> toast(R.string.chat_image_saved)
                                    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ->
                                        toast(R.string.chat_image_save_needs_android10)
                                    else -> toast(R.string.chat_image_save_failed)
                                }
                                when (editing) {
                                    is PendingEdit.Draft ->
                                        viewModel.onReplaceDraftImage(editing.target, exported, context)
                                    is PendingEdit.Message -> {
                                        // 「另存为新草稿附件」而不是覆盖已发送的消息：已发送消息没有
                                        // 本地覆盖文件（服务端也没有单条替换附件的接口），硬造覆盖语义
                                        // 会让本地显示与服务端正式版本各说各话——腐化历史。走与相册同一
                                        // 条暂存路（onImagesPicked）：同一道 4 张产品闸、同一套失败提示。
                                        // exported 不能在这里删：onImagesPicked 在自己的协程里异步读它；
                                        // photo_edit 是缓存目录，系统会回收。
                                        viewModel.onImagesPicked(context, listOf(exported))
                                    }
                                }
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
}

/**
 * 进入图片编辑器的两个来源，编辑机器同一台（PhotoEditor + PhotoEditExporter）：
 *
 * - [Draft]：草稿里的图（拍照进来的）。暂存文件已经在草稿托盘里，编辑结果**覆盖暂存文件**，
 *   UI 走同一张草稿。
 * - [Message]：**已发送消息**大图上的「编辑」。消息没有对应的草稿/暂存文件，编辑结果
 *   作为**新的草稿附件**加入输入框（见 ChatRoute 导出流程的注释）——不回写历史消息。
 */
private sealed interface PendingEdit {
    val bitmap: android.graphics.Bitmap

    data class Draft(val target: java.io.File, override val bitmap: android.graphics.Bitmap) : PendingEdit

    data class Message(override val bitmap: android.graphics.Bitmap) : PendingEdit
}

/** 单次粗定位的等待上限：网络定位通常在 10 秒内出结果，再久用户已经放弃这一条了。 */
private const val LOCATION_TIMEOUT_MS = 15_000L

/** 附件上传需要登录（与旧版一致）：未登录时由 LoginGate 提示并引导登录，返回 false。 */
private fun requireLogin(context: android.content.Context): Boolean =
    com.openapi.feature.chat.ChatDependencies.loginGate().ensureLoggedIn(context)
