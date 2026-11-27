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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarPackStore
import com.openapi.ks.myapp.avatar.BundledAvatars
import com.openapi.ks.myapp.avatar.LayeredAvatar
import com.openapi.ks.myapp.avatar.LayeredAvatarPack
import com.openapi.ks.myapp.avatar.AvatarPortrait
import com.openapi.ks.myapp.avatar.AvatarEventMapper
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.SimpleAvatar
import com.openapi.ks.myapp.avatar.SimpleAvatarRenderer
import com.openapi.ks.myapp.dh.AvatarActionTray
import com.openapi.ks.myapp.dh.DhEngineDownloadGate
import com.openapi.ks.myapp.dh.DhEngineId
import com.openapi.ks.myapp.dh.DhEngineMenuRow
import com.openapi.ks.myapp.dh.DhEngineRegistry
import com.openapi.ks.myapp.dh.DhEngineWatchdog
import com.openapi.ks.myapp.dh.EngineClickAction
import com.openapi.ks.myapp.dh.avatarActions
import com.openapi.ks.myapp.dh.dhEngineLabel
import com.openapi.ks.myapp.dh.DhLabBootstrap
import com.openapi.ks.myapp.dh.DhLabGate
import com.openapi.ks.myapp.dh.DhLabPreferences
import com.openapi.ks.myapp.dh.DigitalHumanLabHost
import com.openapi.ks.myapp.dh.OriginalAvatarDemo
import com.openapi.ks.myapp.dh.OriginalAvatarDemoControls
import com.openapi.ks.myapp.dh.StageChoice
import com.openapi.ks.myapp.dh.dhEngineMenuRows
import com.openapi.ks.myapp.dh.dhEngineSeedHint
import com.openapi.ks.myapp.dh.mapEnsureFailureMessage
import com.openapi.ks.myapp.dh.resolveCallStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState

/** 陪伴界面里能去的页面。聊天是主页面，其余从顶部进入。 */
enum class CompanionPage { CHAT, CHARACTERS, HERS, MEMORY, PROFILE_DOC, SETTINGS }

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
    /**
     * 剧本房 case id（adb `--es script_case …`）。非空时走 createScriptSession，
     * 并在连接后自动进通话页以便听下行。
     */
    scriptCaseId: String? = null,
    /** 打开抽屉。由 HomeScreen 传入，与「对话」页一致。 */
    onMenuClick: (() -> Unit)? = null,
    /**
     * P0-4：聊天页的电话入口跳进陪伴页时置 true——进来就复用页内「进通话」的
     * 同一套决策（含麦克风权限申请），处理后由 [onLaunchCallHandled] 复位，
     * 避免下次回到本页再自动弹一次。
     */
    launchCallOnOpen: Boolean = false,
    onLaunchCallHandled: () -> Unit = {},
    /** 全屏通话覆盖层的挂起状态上报：宿主据此关掉抽屉滑动（VRM 舞台手势要完整序列）。 */
    onCallActiveChanged: (Boolean) -> Unit = {},
    /** 建会话返回 401 时由 app 层负责跳转登录。 */
    onLoginRequired: (() -> Unit)? = null,
    /**
     * 通话页小窗人脸贴纸的内容注入（组合根 HomeScreen 传
     * `CameraFxStickerPip`）。feature 互不依赖，:feature-companion 不 import
     * :feature-camera-fx（ModuleGraphTest 分层规则）。null = 无小窗。
     * [stickerAssetId] = 面板选中的「我」贴纸（相机侧白名单校验）；null = 相机侧默认。
     */
    faceCameraPipContent: (@Composable (Modifier, onGesture: (String) -> Unit, stickerAssetId: String?) -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val tokenProvider = { PreferenceUtils.getPrefString(context, com.openapi.common.net.http.NetConfig.tokenHeader(), null) }
    val api = remember(context) {
        HttpCompanionApi(
            baseUrl = CompanionConfig.baseUrl,
            tokenProvider = tokenProvider,
        )
    }
    // 图片上传：打的是与 `HttpCompanionApi.attachmentUrl` 同一套 `/v1/uploads`，实现
    // 在 :common 的 MediaUploader。没有它就没有发送路径——ViewModel 拿不到上传入口时
    // 会明确报错而不是把一条只有文本的半发消息发出去。
    //
    // 这里另建一个 MediaUploader 而不是向 api 要：api 没有暴露它的 client，为这一个
    // 方法开一条公共口子，比多一个连接池更糟。
    val imageUploader = remember(context) {
        val uploader = com.openapi.common.media.MediaUploader(CompanionConfig.baseUrl, tokenProvider)
        CompanionViewModel.ImageUploader { file, mime -> uploader.upload(file, mime) }
    }

    var page by rememberSaveable { mutableStateOf(CompanionPage.CHAT) }
    var characterId by rememberSaveable { mutableStateOf(initialCharacterId) }
    // 热启动 adb 再带 script_case：rememberSaveable 不会吃新的 initialCharacterId，显式跟上。
    LaunchedEffect(initialCharacterId, scriptCaseId) {
        if (scriptCaseId != null) characterId = initialCharacterId
    }

    val chatViewModel: CompanionViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CompanionViewModel(
                    api = api,
                    transportFactory = { provider -> CompanionTransports.create(context, provider) },
                    mediaUploader = imageUploader,
                )
            }
        },
    )

    // 系统返回键：在子页面时回到聊天页，而不是把整个应用退出（真机上踩到：
    // 子页面只是 page 状态切换，没有返回栈，返回键会直接回到桌面）。
    BackHandler(enabled = page != CompanionPage.CHAT) { page = CompanionPage.CHAT }

    // 全屏通话是覆盖层而不是子页面：它要保持房间连接，退出后回到原来的对话现场，
    // 所以不走 CompanionPage 那套"切换页面"的状态。
    val uiState by chatViewModel.state.collectAsStateWithLifecycle()
    val inCall = uiState.callActive
    DisposableEffect(inCall) {
        onCallActiveChanged(inCall)
        // 离开陪伴页（切标签）时组合退出，兜底复位，别让宿主以为通话还挂着。
        onDispose { onCallActiveChanged(false) }
    }

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

    // 页内通话按钮与聊天页跳入共用这一个入口：权限决策只有一份（见 CallLaunch 的测试）。
    val openCall: () -> Unit = {
        launchCallWithMic(
            hasMicPermission = hasMicPermission(context),
            startCall = { chatViewModel.startCall(micAllowed = it) },
            requestMic = {
                pendingMicAction = PendingMicAction.START_CALL
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }
    LaunchedEffect(launchCallOnOpen) {
        if (launchCallOnOpen) {
            openCall()
            onLaunchCallHandled()
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
                    scriptCaseId = scriptCaseId,
                    modifier = Modifier.fillMaxWidth(),
                    onOpenCall = openCall,
                    onOpenCharacters = { page = CompanionPage.CHARACTERS },
                    onOpenHers = { page = CompanionPage.HERS },
                    onOpenProfile = { page = CompanionPage.MEMORY },
                    onOpenProfileDoc = { page = CompanionPage.PROFILE_DOC },
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

            CompanionPage.HERS -> {
                // her 形象列表（OpenDesign 02）。数据走第二刀那条链：HerRegistryStore 拉
                // /v1/hers（失败宽出成空表），写只写 DhLabPreferences.callStageChoice——
                // 通话页的 resolveCallStage 仍是引擎唯一决定点，这里不另起状态。
                val vm: HerListViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            HerListViewModel(
                                registry = HerRegistryStore(api),
                                api = api,
                                prefs = DhLabPreferences(context),
                                roleId = characterId,
                            )
                        }
                    },
                )
                val herState by vm.state.collectAsStateWithLifecycle()
                HerListScreen(
                    state = herState,
                    onPick = { her ->
                        // 选了即生效：回聊天页再进通话就是新引擎（引擎素材缺失时由既有
                        // DhEngineDownloadGate / 引擎自判兜底，不在这里重复决策）。
                        if (vm.pick(her)) page = CompanionPage.CHAT
                    },
                    onFollowDefault = vm::followServerDefault,
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

            CompanionPage.PROFILE_DOC -> {
                val vm: ProfileDocViewModel = viewModel(
                    factory = viewModelFactory { initializer { ProfileDocViewModel(api, characterId) } },
                )
                val state by vm.state.collectAsStateWithLifecycle()
                ProfileDocScreen(
                    state = state,
                    onRetry = vm::load,
                    onSelectRole = vm::selectRole,
                    onDraftChange = vm::onDraftChange,
                    onSave = vm::save,
                    onDismissSavedHint = vm::dismissSavedHint,
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

        // 波形样式：用户选了就记住，下次进通话还是那一套。
        var waveOptions by remember { mutableStateOf(loadWaveOptions(context)) }
        var callTheme by remember { mutableStateOf(loadCallTheme(context)) }

        // 标题与字标要用她的真名，不能拿 characterId（如 linche）当人看。通话期间拉一次，
        // 失败就退回「陪伴」——名字拿不到不该挡住通话。
        var agentName by rememberSaveable(characterId) { mutableStateOf("陪伴") }
        var voiceLabel by rememberSaveable(characterId) { mutableStateOf<String?>(null) }
        LaunchedEffect(characterId) {
            val summary = runCatching { api.character(characterId).summary }.getOrNull()
                ?: runCatching {
                    api.characters().items.firstOrNull { it.id == characterId }
                }.getOrNull()
            if (summary != null && summary.name.isNotBlank()) agentName = summary.name
            voiceLabel = summary?.voiceLabel
        }

        // 形象包里装了立绘就出图，没有才用首字占位：素材没装时不能假装有图，
        // 一个默认字标比一张「加载中」的空圆更诚实。
        val avatarStore = remember(context) { AvatarPackStore(context) }
        val avatarFiles = remember(characterId, avatarStore) {
            avatarStore.avatarFor(characterId)?.let { avatarStore.load(it) }
        }

        // 分层形象按角色绑定（服务端绑定优先，其次内置绑定表，见 BundledAvatars）。
        // 没有绑定的角色不显示形象，退回圆形头像；不会被兜底成别人的样子。
        val layeredPack by produceState<LayeredAvatarPack?>(initialValue = null, characterId, avatarStore) {
            value = withContext(Dispatchers.IO) {
                val key = BundledAvatars.keyFor(characterId, avatarStore) ?: return@withContext null
                if (BundledAvatars.isBundled(key)) runCatching { BundledAvatars.ensureDemo(context, avatarStore) }
                avatarStore.loadLayered(key)
            }
        }

        // DH Lab 只决定右上角下拉里有没有「形象」段，不改默认画面。
        // 默认（callStage == null）仍是原来的圆头像 / 分层立绘；选了引擎才换舞台。
        DhLabBootstrap.ensureDefaults()
        val labEnabled = remember(context) { DhLabGate.isEnabled(context) }
        val faceCamPrefs = remember(context) { CompanionFaceCameraPrefs(context) }
        var faceCameraOn by remember { mutableStateOf(faceCamPrefs.enabled) }
        // 面板「我」区选中的贴纸资产 id；null = 相机模块默认（摸头爱心）。
        var userSticker by rememberSaveable { mutableStateOf<String?>(null) }
        val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            faceCameraOn = granted
            faceCamPrefs.enabled = granted
            if (!granted) toast(context, "没有相机权限，无法打开预览")
        }
        val dhPrefs = remember(context) { DhLabPreferences(context) }
        // 舞台引擎的唯一选择点（客户端第二刀，persona/her 分层架构 §4「进房」her 半）：
        // 用户显式选过（原形象 / 某引擎）→ 听用户的；从没选过 → 按该角色默认 her 的
        // renderer。注册表拉取失败宽出成空表（HerRegistryStore），没有 her / renderer
        // 认不出 → null = 原来的分层立绘，绝不因为拉取失败换脸或白屏。
        // 注意 stageChoice 只在引擎菜单里写（行 id 为 null = 显式「原来的形象」）。
        var stageChoice by remember { mutableStateOf(dhPrefs.callStageChoice) }
        val herRegistry = remember(api) { HerRegistryStore(api) }
        val hers by produceState<List<HerSummary>>(initialValue = emptyList(), characterId) {
            value = withContext(Dispatchers.IO) { herRegistry.hers() }
        }
        val callStage = resolveCallStage(stageChoice, characterId, hers)
        // 用户在菜单里再点已勾选的引擎 = 「解除熔断再试一次」。引擎 id 没变，Compose 不会重组，
        // 所以用计数器重建 LabHost（key），让它重新读隔离状态。
        var engineRetry by remember { mutableIntStateOf(0) }
        // 长按她 → 动作托盘（贴在舞台上的一层薄纱）。只在数字人引擎舞台生效，
        // 「原来的形象」分层立绘保持原样不受影响。
        var actionTrayOpen by rememberSaveable { mutableStateOf(false) }
        // Live2D 多形象（用户 2026-11-23「demo里有好几个形象，是否可以都集成建立可切换」）：
        // 偏好持久化，托盘「换形象」行在设备上真实存在的模型里循环。
        var live2dModel by remember { mutableStateOf(dhPrefs.live2dModel) }
        var demoSmileEnabled by rememberSaveable { mutableStateOf(false) }
        var demoCapeEnabled by rememberSaveable { mutableStateOf(false) }
        // 当前在岗的引擎实例快照（LabHost 在切引擎/切形象时更新，销毁时回 null）。
        // 换装行与换形象行直接调它——引擎内部状态不可观察，靠这个引用把托盘和引擎接上。
        var stageEngine by remember { mutableStateOf<com.openapi.ks.myapp.dh.DigitalHumanEngine?>(null) }
        // 热切换保留同一引擎引用，单独发布模型名才能让标签和下一次切换读取新值。
        // Source: https://developer.android.com/develop/ui/compose/state#state-in-composables
        var stageModel by remember { mutableStateOf<String?>(null) }
        // 换装/换形象后 +1：托盘行尾的当前值需要重组才刷新（引擎状态不是 Compose state）。
        var wardrobeTick by remember { mutableIntStateOf(0) }
        val uiScope = rememberCoroutineScope()
        // 重型引擎的素材没下好时，点菜单先下载；下载成功后**重查引擎可用性**再换舞台——
        // 素材下齐但 native 缺失时不换（否则舞台上静默退回 Simple）。Simple / 原形象不受影响。
        val engineGate = remember(context) {
            val store = DhLabBootstrap.assetStore(context)
            DhEngineDownloadGate(
                availability = { id ->
                    DhEngineRegistry.available(context).firstOrNull { it.id == id }?.available == true
                },
                ensure = { assets, onProgress -> store.ensure(assets, onProgress) },
            )
        }
        // 下载/失败后 +1：菜单行（未下载 / native 库缺失 / 机型不足）跟着引擎状态刷新。
        var engineMenuTick by remember { mutableIntStateOf(0) }
        val engineMenu = remember(context, labEnabled, engineMenuTick) {
            if (!labEnabled) {
                null
            } else {
                val rows = dhEngineMenuRows(DhEngineRegistry.available(context))
                val debuggable = context.applicationInfo.flags and
                    android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
                if (debuggable) {
                    rows + listOf(
                        DhEngineMenuRow(
                            id = DhEngineId.LIVE2D,
                            label = "原创 Live2D Demo",
                            switches = true,
                            detail = "debug",
                            demoModel = OriginalAvatarDemo.MODEL_DIR,
                        ),
                        DhEngineMenuRow(
                            id = null,
                            label = "数字人样例实验室",
                            switches = true,
                            detail = "debug · 离线样例",
                            demoModel = "__avatar_lab_gallery__",
                        ),
                    )
                } else {
                    rows
                }
            }
        }

        // 通话页用**会动的**形象，而不是单张立绘：这里是她被盯着看最久的地方，
        // 一张不眨眼、不说话的照片比不显示还糟。嘴型由远端音频振幅驱动，
        // 眨眼/呼吸由渲染器自己跑。素材缺失时退回单张立绘，再缺就退回字标。
        val callAvatarRenderer = remember { SimpleAvatarRenderer() }
        LaunchedEffect(avatarFiles) {
            avatarFiles?.let { callAvatarRenderer.bind(AvatarSpec(files = it)) }
        }
        // 状态与表情要喂给通话页的渲染器。没有这两跳，渲染器永远停在 IDLE，
        // 而 Mouth 指令在非 SPEAKING 时被整条丢弃——头像的嘴根本不会动；
        // 她的表情同理到不了脸上。chat 页同样的接线在 CompanionScreen。
        LaunchedEffect(uiState.avatarState) {
            AvatarEventMapper.mapState(uiState.avatarState)?.let(callAvatarRenderer::dispatch)
        }
        LaunchedEffect(uiState.emotion) {
            // 与 chat 页同一跳：null → ClearEmotion，降级清场时表情真的下脸。
            callAvatarRenderer.dispatch(AvatarEventMapper.emotionCommand(uiState.emotion))
        }
        DisposableEffect(callAvatarRenderer) {
            onDispose { callAvatarRenderer.release() }
        }
        val avatarSlot: (@Composable () -> Unit)? = when {
            avatarFiles != null -> {
                {
                    SimpleAvatar(
                        renderer = callAvatarRenderer,
                        modifier = Modifier.fillMaxSize(),
                        mouthLevel = chatViewModel.mouthLevel,
                    )
                }
            }
            else -> avatarFiles?.portrait?.let { file ->
                { AvatarPortrait(file = file, modifier = Modifier.fillMaxSize()) }
            }
        }

        // 计时从进通话页开始（重连期间暂停，不清零）。状态放在覆盖层里，挂断再进来自然从 0 起。
        var elapsedSeconds by remember { mutableLongStateOf(0L) }
        LaunchedEffect(uiState.connection) {
            while (uiState.connection == CompanionConnection.CONNECTED) {
                delay(1_000)
                elapsedSeconds += 1
            }
        }

        // 两条不同的电平：
        // - mouthLevel = 她的声音，驱动头像呼吸环与嘴型；
        // - selfLevel  = 你的声音，驱动底部波形。
        // 以前两者共用一个，结果就是「你说话波形不动」——那个波形画的是她的音量。
        val mouthLevel by chatViewModel.mouthLevel.collectAsStateWithLifecycle()
        val selfLevel by chatViewModel.selfLevel.collectAsStateWithLifecycle()
        val selfBands by chatViewModel.selfBands.collectAsStateWithLifecycle()

        val audioPreview = remember { CallAudioPreview(context) }
        val preview by audioPreview.state.collectAsStateWithLifecycle()
        val previewActive = preview.speaker != null
        DisposableEffect(audioPreview) {
            onDispose {
                audioPreview.close()
                chatViewModel.setAudioPreviewActive(false)
            }
        }
        LaunchedEffect(previewActive, uiState.isMuted) {
            chatViewModel.setAudioPreviewActive(previewActive)
        }
        val labeledCaptions = uiState.messages.filter { it.text.isNotBlank() }.map {
            CallCaption(it.text, if (it.role == MessageRole.USER) "你" else agentName, it.streaming)
        } + if (previewActive && preview.text.isNotBlank()) listOf(
            CallCaption(preview.text, if (preview.speaker == "user") "你 · 试听" else "$agentName · 试听"),
        ) else emptyList()

        // 退出通话页时静音播放：房间不断开（通话只是形态），但用户的心智是
        // 「我挂断了，她不该还在说话」。进来时恢复。
        DisposableEffect(Unit) {
            chatViewModel.setPlaybackAudible(true)
            onDispose { chatViewModel.setPlaybackAudible(false) }
        }

        val callConnection = when (uiState.connection) {
            CompanionConnection.CONNECTED -> CallConnection.CONNECTED
            CompanionConnection.RECONNECTING -> CallConnection.RECONNECTING
            // 断开/出错不能显示「正在接通」：她那边已经没声音了，界面要给出原因。
            CompanionConnection.ERROR -> CallConnection.ENDED
            CompanionConnection.IDLE, CompanionConnection.CONNECTING -> CallConnection.CONNECTING
        }

        val currentPreview by rememberUpdatedState(preview)
        val mouthFlow = chatViewModel.mouthLevel
        val aiMouthLevel: () -> Float = {
            val pv = currentPreview
            if (pv.speaker != null) { if (pv.speaker == "ai") pv.level else 0f } else mouthFlow.value
        }

        fun openOriginalAvatarDemo() {
            uiScope.launch {
                val result = withContext(Dispatchers.IO) { OriginalAvatarDemo.install(context) }
                result.fold(
                    onSuccess = {
                        engineMenuTick++
                        demoSmileEnabled = false
                        demoCapeEnabled = false
                        live2dModel = OriginalAvatarDemo.MODEL_DIR
                        stageChoice = StageChoice.Engine(DhEngineId.LIVE2D)
                        dhPrefs.callStageChoice = StageChoice.Engine(DhEngineId.LIVE2D)
                        DhEngineWatchdog(context).clear(DhEngineId.LIVE2D)
                        engineRetry++
                        toast(context, "原创 Live2D Demo 已就绪")
                    },
                    onFailure = { error ->
                        toast(context, "原创 Live2D Demo 加载失败：${error.localizedMessage ?: "资源不可用"}")
                    },
                )
            }
        }

        CompanionCallScreen(
            state = CompanionCallState(
                agentName = agentName,
                voiceLabel = voiceLabel,
                captions = transcript,
                speakerCaptions = labeledCaptions,
                elapsedSeconds = elapsedSeconds,
                isMuted = uiState.isMuted,
                cameraActive = false,
                // 她的音量：驱动呼吸环与「她在说话」的视觉强度。
                audioLevel = if (callConnection != CallConnection.CONNECTED) 0f
                    else if (previewActive) { if (preview.speaker == "ai") preview.level else 0f } else mouthLevel,
                // 你的音量：驱动底部波形。静音或连接断开时归零，防止断线后残留最后一帧电平。
                selfLevel = if (callConnection != CallConnection.CONNECTED || uiState.isMuted) 0f
                    else if (previewActive) { if (preview.speaker == "user") preview.level else 0f } else selfLevel,
                // 你的声音的频段：断线/静音清空；试听时取试听音频的频段。
                selfBands = if (callConnection != CallConnection.CONNECTED || uiState.isMuted) FloatArray(0)
                    else if (previewActive) {
                        if (preview.speaker == "user") preview.bands else FloatArray(0)
                    } else selfBands,
                isAgentSpeaking = callConnection == CallConnection.CONNECTED && uiState.messages.lastOrNull()?.streaming == true,
                connection = callConnection,
                statusHint = when (uiState.connection) {
                    CompanionConnection.ERROR ->
                        uiState.connectionError ?: uiState.error ?: "连接已断开"
                    CompanionConnection.RECONNECTING -> "正在重连…"
                    else -> null
                },
                loginRequired = uiState.callLoginRequired,
            ),
            // 摄像头目前不支持；由按钮显示不可用状态，不注入点击回调。
            cameraAvailable = false,
            previewState = preview,
            // 试听按钮只是调试工具：正式包不显示（onPreviewAudio == null 时整行不渲染）。
            onPreviewAudio = if (
                context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
            ) audioPreview::toggle else null,
            waveOptions = waveOptions,
            onWaveOptionsChange = { next ->
                waveOptions = next
                saveWaveOptions(context, next)
            },
            faceCameraEnabled = faceCameraOn,
            onFaceCameraChange = if (labEnabled) {
                { on ->
                    if (on && context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        cameraLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        faceCameraOn = on
                        faceCamPrefs.enabled = on
                    }
                }
            } else {
                null
            },
            theme = callTheme,
            onThemeChange = { next ->
                callTheme = next
                saveCallTheme(context, next)
            },
            onHangUp = { chatViewModel.endCall() },
            onLoginRequired = onLoginRequired,
            onRetryConnect = if (uiState.connection == CompanionConnection.ERROR && !uiState.callLoginRequired) {
                { chatViewModel.retryConnection(characterId, scriptCaseId) }
            } else {
                null
            },
            onToggleMute = {
                // 静音状态下点「取消静音」就是真的要开麦，没权限得先申请。
                if (uiState.isMuted && !hasMicPermission(context)) {
                    pendingMicAction = PendingMicAction.UNMUTE
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    chatViewModel.toggleMute()
                }
            },
            onToggleCamera = {},
            onSendText = { text ->
                chatViewModel.onInputChange(text)
                chatViewModel.send()
            },
            avatarContent = avatarSlot,
            // 数字人引擎舞台 = 主角模式（人物铺满整块舞台）；「原来的形象」分层立绘
            // 与圆形头像保持原有分栏布局不变。
            characterHero = callStage != null,
            engineMenu = engineMenu,
            selectedEngine = callStage,
            wardrobeRows = stageEngine?.wardrobeRows().orEmpty(),
            wardrobeRefreshTick = wardrobeTick,
            onWardrobeAxisClick = if (callStage != null) {
                { axis ->
                    stageEngine?.advanceWardrobe(axis)
                    wardrobeTick++
                }
            } else null,
            live2dModelLabel = stageModel,
            onLive2dModelSwitch = if (callStage != null) {
                val present = com.openapi.ks.myapp.dh.live2dPresentModels(context)
                val model = stageModel
                if (present.size > 1 && model != null) {
                    {
                        val next = com.openapi.ks.myapp.dh.Live2dModels.next(model, present)
                        live2dModel = next
                        dhPrefs.live2dModel = next
                        wardrobeTick++
                    }
                } else null
            } else null,
            live2dModelSwitchHint = "只推了这一个形象",
            onEngineMenuClick = { row ->
                if (row.demoModel == "__avatar_lab_gallery__") {
                    context.startActivity(android.content.Intent().setClassName(context, "com.openapi.avatar.lab.MainActivity"))
                } else if (row.demoModel != null) {
                    openOriginalAvatarDemo()
                } else when (engineGate.actionFor(row)) {
                    EngineClickAction.UNAVAILABLE_REASON -> {
                        val id = row.id
                        val msg = when {
                            id != null && row.detail?.contains("seed") == true ->
                                dhEngineSeedHint(id) ?: row.detail
                            else -> row.detail ?: "这个形象现在不能用"
                        }
                        toast(context, msg)
                    }
                    EngineClickAction.SELECT -> {
                        // 用户主动再选 = 解除上次崩溃留下的熔断（再崩会重新熔断）。
                        row.id?.let { DhEngineWatchdog(context).clear(it) }
                        engineRetry++
                        if (live2dModel == OriginalAvatarDemo.MODEL_DIR) {
                            live2dModel = dhPrefs.live2dModel
                        }
                        // 菜单里点任何一行都是**显式**表态（含「原来的形象」= row.id null）：
                        // 存成 StageChoice，服务端 her 从此不再有权决定这个用户的舞台。
                        val choice = row.id?.let { StageChoice.Engine(it) } ?: StageChoice.Original
                        stageChoice = choice
                        dhPrefs.callStageChoice = choice
                    }
                    EngineClickAction.NOT_READY_START_DOWNLOAD -> {
                        val id = row.id
                        if (id != null) {
                            val name = dhEngineLabel(id)
                            uiScope.launch {
                                if (engineGate.isDownloading(id)) {
                                    toast(context, "$name 正在下载…")
                                    return@launch
                                }
                                toast(context, "正在下载 $name 素材…")
                                // onProgress 在 IO 线程回调，v1 不展示进度，传空实现；只在下载结束后（主线程）切换。
                                when (val outcome = engineGate.download(id) {}) {
                                    DhEngineDownloadGate.Outcome.Success -> {
                                        // 素材下好 ≠ 能用：native 缺失 / 模型不全时引擎自判仍不可用，
                                        // 换舞台只会静默退回 Simple。重查，不可用就报原因、不换。
                                        engineMenuTick++
                                        val now = DhEngineRegistry.available(context)
                                            .firstOrNull { it.id == id }
                                        if (now?.available == true) {
                                            DhEngineWatchdog(context).clear(id)
                                            val choice = StageChoice.Engine(id)
                                            stageChoice = choice
                                            dhPrefs.callStageChoice = choice
                                        } else {
                                            toast(context, "$name 素材已下好，但${now?.reason ?: "现在还不能用"}")
                                        }
                                    }
                                    DhEngineDownloadGate.Outcome.Busy ->
                                        toast(context, "$name 正在下载…")
                                    is DhEngineDownloadGate.Outcome.Failed -> {
                                        engineMenuTick++
                                        val why = mapEnsureFailureMessage(id, outcome.reason)
                                        toast(context, "$name：$why")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            // 只有下拉里点了某个引擎才换舞台；默认仍是原来的分层立绘。
            avatarStage = when (val chosen = callStage) {
                null -> layeredPack?.let { pack ->
                    {
                        LayeredAvatar(
                            pack = pack,
                            modifier = Modifier.fillMaxSize(),
                            mouthLevel = { aiMouthLevel() },
                            onTapped = chatViewModel::onAvatarTapped,
                        )
                    }
                }
                else -> {
                    {
                        Box(Modifier.fillMaxSize()) {
                        key(engineRetry) {
                        DigitalHumanLabHost(
                            spec = avatarFiles?.let { AvatarSpec(files = it) },
                            mouthLevel = chatViewModel.mouthLevel,
                            remotePcm = chatViewModel.remotePcm,
                            modifier = Modifier.fillMaxSize(),
                            engineId = chosen,
                            // 点她一下：本地立刻有反馈（舞台脉冲），区域上报给 agent 决定回不回。
                            onTapped = chatViewModel::onAvatarTapped,
                            // 长按她：动作托盘弹出；选了可点动作 = 把文案当用户的话发出去。
                            onLongPressed = { actionTrayOpen = true }, // 托盘仅保留聊天类动作；换装在收纳盒
                            // 测试语音 rig 走生产注入路径：PCM 从远端音频轨回调共用的
                            // 下游入口进 transport，下游电平→嘴型与真通话完全同路。
                            audioInjector = chatViewModel::injectLabAudioPcm16k,
                            // 主题背景色穿透到引擎舞台（Live2D 的 GL 清屏色），
                            // 换主题不再「还是大黑色」。
                            theme = callTheme,
                            // Live2D 多形象：偏好持久化的模型目录名；变化时 LabHost
                            // 原地换引擎实例（先 release 再 create）。
                            live2dModel = live2dModel,
                            onEngineChanged = {
                                stageEngine = it
                                stageModel = it?.modelLabel()
                            },
                            // Simple 舞台形象回退（投诉 2026-11-23「只有一个字」）：
                            // 没有旧格式形象包但有分层立绘的角色，舞台画立绘不画字。
                            simpleFallback = if (avatarFiles == null) layeredPack else null,
                            // 特效层：订阅既有 gesture/gestureSeq（cmp.cue → Gesture），不另起总线。
                            gestureId = uiState.gesture,
                            gestureSeq = uiState.gestureSeq,
                            particles = waveOptions.particles,
                            // dh-lab 调试面板 → 同一条 Gesture 词表（按选项的 gestureId）。
                            onLabFxTest = { gesture -> chatViewModel.emitStageGesture(gesture) },
                            // 面板「我」区：选中的贴纸只换本机相机小窗，不进舞台/发送视频。
                            onLabUserStickerPick = { assetId -> userSticker = assetId },
                            selectedUserSticker = userSticker,
                            labFaceCameraPip = labEnabled && faceCameraOn,
                            // 小窗手势 → 同一条 Gesture 词表（张→wave、捏→pat_head）。
                            // 词表映射钉在 feature-camera-fx 的 CameraGestureCue（单一事实源）。
                            onCameraStageGesture = if (faceCameraOn) {
                                { cue -> chatViewModel.emitStageGesture(cue) }
                            } else {
                                null
                            },
                            faceCameraPipContent = faceCameraPipContent,
                        )
                        }
                        if (labEnabled && !actionTrayOpen && chosen == DhEngineId.LIVE2D &&
                            stageEngine?.id == DhEngineId.LIVE2D &&
                            stageModel == OriginalAvatarDemo.MODEL_DIR
                        ) {
                            OriginalAvatarDemoControls(
                                smileEnabled = demoSmileEnabled,
                                capeEnabled = demoCapeEnabled,
                                onSmileChange = { enabled ->
                                    demoSmileEnabled = enabled
                                    stageEngine?.dispatch(
                                        AvatarCommand.Emotion(
                                            OriginalAvatarDemo.expressionName(enabled, demoCapeEnabled),
                                        ),
                                    )
                                },
                                onCapeChange = { enabled ->
                                    demoCapeEnabled = enabled
                                    stageEngine?.dispatch(
                                        AvatarCommand.Emotion(
                                            OriginalAvatarDemo.expressionName(demoSmileEnabled, enabled),
                                        ),
                                    )
                                },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                            )
                        }
                        if (actionTrayOpen) {
                            val wardrobe = stageEngine?.wardrobeRows().orEmpty()
                            val model = stageModel
                            val present = com.openapi.ks.myapp.dh.live2dPresentModels(context)
                            AvatarActionTray(
                                actions = avatarActions(),
                                onActionClick = { action ->
                                    action.messageText?.let(chatViewModel::sendActionText)
                                    chatViewModel.emitStageGesture(action.id)
                                    actionTrayOpen = false
                                },
                                onDismiss = { actionTrayOpen = false },
                                wardrobeRows = wardrobe,
                                onWardrobeClick = { axis ->
                                    stageEngine?.advanceWardrobe(axis)
                                    wardrobeTick++
                                },
                                modelLabel = model,
                                onModelSwitch = if (present.size > 1 && model != null) {
                                    {
                                        val next = com.openapi.ks.myapp.dh.Live2dModels.next(model, present)
                                        live2dModel = next
                                        dhPrefs.live2dModel = next
                                        wardrobeTick++
                                    }
                                } else null,
                                modelSwitchHint = "只推了这一个形象",
                                refreshTick = wardrobeTick,
                            )
                        }
                        }
                    }
                }
            },
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

// ---- 波形样式偏好（本地，不涉及服务端） ----

private const val WAVE_PREFS = "companion_wave"

/**
 * 读用户选过的波形样式。新结构分底部样式（bottom）与中央效果（center）两个键。
 * 兼容旧版本只存一个 "style" 的情况：旧值里 Orbit/Blob/Blast 本就是中央效果，
 * 迁移到 center；Wave/Bars/Ribbon 留在 bottom；认不出来的字符串回落到各自默认。
 */
internal fun loadWaveOptions(context: Context): WaveOptions {
    val prefs = context.getSharedPreferences(WAVE_PREFS, Context.MODE_PRIVATE)

    val bottom = prefs.getString("bottom", null)?.let {
        runCatching { BottomWaveStyle.valueOf(it) }.getOrNull()
    } ?: migrateLegacyBottom(prefs.getString("style", null))

    val center = prefs.getString("center", null)?.let {
        runCatching { CenterEffect.valueOf(it) }.getOrNull()
    } ?: migrateLegacyCenter(prefs.getString("style", null))

    return WaveOptions(
        bottom = bottom,
        center = center,
        particles = prefs.getBoolean("particles", true),
    )
}

private fun migrateLegacyBottom(legacy: String?): BottomWaveStyle = when (legacy) {
    "Bars" -> BottomWaveStyle.Bars
    "Ribbon" -> BottomWaveStyle.Ribbon
    "Wave" -> BottomWaveStyle.Wave
    else -> BottomWaveStyle.Wave
}

private fun migrateLegacyCenter(legacy: String?): CenterEffect = when (legacy) {
    "Orbit" -> CenterEffect.Orbit
    "Blob" -> CenterEffect.Blob
    "Blast" -> CenterEffect.Blast
    else -> CenterEffect.Ring
}

internal fun saveWaveOptions(context: Context, options: WaveOptions) {
    context.getSharedPreferences(WAVE_PREFS, Context.MODE_PRIVATE).edit()
        .putString("bottom", options.bottom.name)
        .putString("center", options.center.name)
        .putBoolean("particles", options.particles)
        // 旧键清掉，避免下次安装旧版本又把迁移值读回去造成来回跳。
        .remove("style")
        .apply()
}

object CompanionConfig {
    /**
     * 本地真机验收用的地址覆盖（runbook `local-livekit-device-runbook.md` §1，Phase 2 D4 前置）。
     *
     * **只有 debug 包能填上它**：唯一的写入方是 debug 源集里的 `DevCompanionServer`
     * （`app/src/debug/`，release 的源码与清单里都没有这个类），它读 app 私有 prefs 的
     * `companion_base_url` 键。release 下这个字段恒为 null，`baseUrl` 与改动前逐字节相同。
     *
     * 刻意**不动 `Constants.BASE_IP`**：那只把陪伴 API 指本地，频道/放映厅等其它功能仍走生产，
     * 不互相踩。APK 里因此不含任何环境地址——指哪套全在装机后用 adb 写 prefs。
     */
    var debugOverride: String? = null

    /** 服务端与主站同一网关，路径由 HttpCompanionApi 拼上 /companion/... 前缀。 */
    val baseUrl: String get() = debugOverride ?: "https://${Constants.BASE_IP}"
}

/** 默认角色。角色列表选过之后以列表为准，这里只保证第一次进来有一个角色。 */
const val DEFAULT_CHARACTER_ID = "linche"
