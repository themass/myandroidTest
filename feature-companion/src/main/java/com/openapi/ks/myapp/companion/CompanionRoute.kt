package com.openapi.ks.myapp.companion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.openapi.core.config.CommonConstants
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.feature.companion.R
import com.openapi.core.config.Constants
import com.openapi.ks.myapp.avatar.AvatarPackStore
import com.openapi.ks.myapp.avatar.AvatarPortrait
import kotlinx.coroutines.delay

/** 陪伴界面里能去的页面。聊天是主页面，其余三个从顶部进入。 */
enum class CompanionPage { CHAT, CHARACTERS, MEMORY, SETTINGS }

/**
 * 陪伴界面的完整装配：聊天主页面 + 角色列表 / 记忆 / 偏好三个子页面。
 *
 * API 在这里创建（地址与登录 token 来自现有配置），ViewModel 只拿接口，不认 HTTP。
 * 默认角色是林澈；在角色列表里选了别的，会话切到那个角色。
 */
@Composable
fun CompanionRoute(
    modifier: Modifier = Modifier,
    initialCharacterId: String = DEFAULT_CHARACTER_ID,
    /** 打开抽屉。由 HomeScreen 传入，与「对话」页一致。 */
    onMenuClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val api = remember(context) {
        HttpCompanionApi(
            baseUrl = CompanionConfig.baseUrl,
            tokenProvider = { PreferenceUtils.getPrefString(context, com.openapi.common.net.http.NetConfig.tokenHeader(), null) },
        )
    }

    var page by rememberSaveable { mutableStateOf(CompanionPage.CHAT) }
    var characterId by rememberSaveable { mutableStateOf(initialCharacterId) }

    val chatViewModel: CompanionViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CompanionViewModel(api = api, transport = LiveKitCompanionTransport(context)) }
        },
    )

    // 系统返回键：在子页面时回到聊天页，而不是把整个应用退出（真机上踩到：
    // 子页面只是 page 状态切换，没有返回栈，返回键会直接回到桌面）。
    BackHandler(enabled = page != CompanionPage.CHAT) { page = CompanionPage.CHAT }

    // 全屏通话是覆盖层而不是子页面：它要保持房间连接，退出后回到原来的对话现场，
    // 所以不走 CompanionPage 那套"切换页面"的状态。
    val uiState by chatViewModel.state.collectAsStateWithLifecycle()
    val inCall = uiState.callActive

    // 进通话先要有录音权限：没有就申请；用户拒绝也照样进，只是以静音形态进（她能说，你只能看/打字）。
    // 用一个待办动作区分「拒绝后该继续做什么」——申请前不知道回调回来对应哪件事。
    var pendingMicAction by remember { mutableStateOf(PendingMicAction.NONE) }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingMicAction
        pendingMicAction = PendingMicAction.NONE
        when (action) {
            PendingMicAction.START_CALL -> chatViewModel.startCall(micAllowed = granted)
            PendingMicAction.UNMUTE ->
                if (granted) {
                    chatViewModel.toggleMute()
                } else {
                    toast(context, "没有麦克风权限，先以静音方式继续")
                }

            PendingMicAction.NONE -> Unit
        }
    }

    // 系统返回键也走二次确认：全屏通话里返回 = 挂断，误触的代价是一次断线。
    var backArmed by remember { mutableStateOf(false) }
    LaunchedEffect(backArmed) {
        if (backArmed) {
            delay(CALL_BACK_CONFIRM_WINDOW_MS)
            backArmed = false
        }
    }
    LaunchedEffect(inCall) { if (!inCall) backArmed = false }
    BackHandler(enabled = inCall) {
        if (backArmed) {
            chatViewModel.endCall()
        } else {
            backArmed = true
            toast(context, "再按一次返回键挂断通话")
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 离开聊天页就停掉收件箱轮询，回来时由 CompanionScreen 的 start() 重新拉起。
        LaunchedEffect(page) {
            if (page != CompanionPage.CHAT) chatViewModel.stopListening()
        }
        when (page) {
            CompanionPage.CHAT -> {
                // 顶部栏只由 CompanionScreen 里那一条渲染（它带抽屉入口，与对话页一致）。
                // 这里原来还有一条 TopActions，两条叠在一起，屏幕上出现两行「陪伴」标题。
                CompanionScreen(
                    viewModel = chatViewModel,
                    characterId = characterId,
                    modifier = Modifier.fillMaxWidth(),
                    onOpenCall = {
                        if (hasMicPermission(context)) {
                            chatViewModel.startCall(micAllowed = true)
                        } else {
                            pendingMicAction = PendingMicAction.START_CALL
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onOpenCharacters = { page = CompanionPage.CHARACTERS },
                    onOpenProfile = { page = CompanionPage.MEMORY },
                    onMenuClick = onMenuClick,
                )
            }

            CompanionPage.CHARACTERS -> {
                val vm: CharacterListViewModel = viewModel(
                    factory = viewModelFactory { initializer { CharacterListViewModel(api) } },
                )
                val state by vm.state.collectAsStateWithLifecycle()
                CharacterListScreen(
                    state = state,
                    onRetry = vm::load,
                    currentCharacterId = characterId,
                    onPick = {
                        characterId = it.id
                        page = CompanionPage.CHAT
                    },
                    onBack = { page = CompanionPage.CHAT },
                )
            }

            CompanionPage.MEMORY -> {
                val vm: MemoryViewModel = viewModel(
                    factory = viewModelFactory { initializer { MemoryViewModel(api, characterId) } },
                )
                val state by vm.state.collectAsStateWithLifecycle()
                MemoryScreen(
                    state = state,
                    onRetry = vm::reload,
                    onDeleteFact = vm::deleteFact,
                    onDeleteEvent = vm::deleteEvent,
                    onMarkDone = vm::markEventDone,
                    onAddEvent = vm::addEvent,
                    onSuppressProfile = vm::suppressProfile,
                    onDeleteProfile = vm::deleteProfile,
                    onBack = { page = CompanionPage.CHAT },
                )
            }

            CompanionPage.SETTINGS -> {
                val vm: CompanionSettingsViewModel = viewModel(
                    factory = viewModelFactory { initializer { CompanionSettingsViewModel(api) } },
                )
                val state by vm.state.collectAsStateWithLifecycle()
                CompanionSettingsScreen(
                    state = state,
                    onRetry = vm::load,
                    onChange = vm::update,
                    onPauseHour = { vm.pauseUntil(utcIsoAfterHours(1)) },
                    onResume = { vm.pauseUntil("") },
                    onBack = { page = CompanionPage.CHAT },
                )
            }
        }
    }

    // 通话覆盖层：只在需要时组合，挂上去后旧界面不再接收点击。
    if (inCall) {
        val transcript = remember(uiState.messages) {
            uiState.messages.filter { it.role == MessageRole.ASSISTANT }.map { it.text }.filter { it.isNotBlank() }
        }

        // 标题与字标要用她的真名，不能拿 characterId（如 linche）当人看。通话期间拉一次，
        // 失败就退回「陪伴」——名字拿不到不该挡住通话。
        var agentName by rememberSaveable(characterId) { mutableStateOf("陪伴") }
        LaunchedEffect(characterId) {
            val name = runCatching { api.character(characterId).summary.name }.getOrNull()
                ?: runCatching {
                    api.characters().items.firstOrNull { it.id == characterId }?.name
                }.getOrNull()
            if (!name.isNullOrBlank()) agentName = name
        }

        // 形象包里装了立绘就出图，没有才用首字占位：素材没装时不能假装有图，
        // 一个默认字标比一张「加载中」的空圆更诚实。
        val avatarStore = remember(context) { AvatarPackStore(context) }
        val portraitFile = remember(characterId, avatarStore) {
            avatarStore.avatarFor(characterId)?.let { avatarStore.load(it)?.portrait }
        }
        val avatarSlot: (@Composable () -> Unit)? = portraitFile?.let { file ->
            { AvatarPortrait(file = file, modifier = Modifier.fillMaxSize()) }
        }

        // 计时从进通话页开始（重连期间暂停，不清零）。状态放在覆盖层里，挂断再进来自然从 0 起。
        var elapsedSeconds by remember { mutableLongStateOf(0L) }
        LaunchedEffect(uiState.connection) {
            while (uiState.connection == CompanionConnection.CONNECTED) {
                delay(1_000)
                elapsedSeconds += 1
            }
        }

        val callConnection = when (uiState.connection) {
            CompanionConnection.CONNECTED -> CallConnection.CONNECTED
            CompanionConnection.RECONNECTING -> CallConnection.RECONNECTING
            // 断开/出错不能显示「正在接通」：她那边已经没声音了，界面要给出原因。
            CompanionConnection.ERROR -> CallConnection.ENDED
            CompanionConnection.IDLE, CompanionConnection.CONNECTING -> CallConnection.CONNECTING
        }

        CompanionCallScreen(
            state = CompanionCallState(
                agentName = agentName,
                captions = transcript,
                elapsedSeconds = elapsedSeconds,
                isMuted = uiState.isMuted,
                cameraActive = false,
                // 电平暂时没有数据源：服务端把 TTS 音轨推给房间，客户端还没有把它接到这里。
                audioLevel = 0f,
                isAgentSpeaking = uiState.messages.lastOrNull()?.streaming == true,
                connection = callConnection,
                statusHint = if (uiState.connection == CompanionConnection.ERROR) {
                    uiState.error ?: "连接已断开"
                } else {
                    null
                },
            ),
            // 摄像头还没接：按钮置灰，点了给一句明确说法，而不是点了没反应。
            cameraAvailable = false,
            onHangUp = { chatViewModel.endCall() },
            onToggleMute = {
                // 静音状态下点「取消静音」就是真的要开麦，没权限得先申请。
                if (uiState.isMuted && !hasMicPermission(context)) {
                    pendingMicAction = PendingMicAction.UNMUTE
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    chatViewModel.toggleMute()
                }
            },
            onToggleCamera = { toast(context, "摄像头即将支持，先用声音陪她吧") },
            onSendText = { text ->
                chatViewModel.onInputChange(text)
                chatViewModel.send()
            },
            avatarContent = avatarSlot,
        )
    }
    }
}

/** 录音权限申请回来后要接着做的事。申请前不知道回调对应哪件事，所以先记下来。 */
private enum class PendingMicAction { NONE, START_CALL, UNMUTE }

/** 返回键二次确认窗口，与通话页挂断按钮保持一致的三秒。 */
private const val CALL_BACK_CONFIRM_WINDOW_MS = 3_000L

private fun hasMicPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

object CompanionConfig {
    /** 服务端与主站同一网关，路径由 HttpCompanionApi 拼上 /companion/... 前缀。 */
    val baseUrl: String get() = "https://${Constants.BASE_IP}"
}

/** 默认角色。角色列表选过之后以列表为准，这里只保证第一次进来有一个角色。 */
const val DEFAULT_CHARACTER_ID = "linche"
