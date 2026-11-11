package com.openapi.ks.myapp.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.openapi.ks.myapp.avatar.SimpleAvatar
import com.openapi.core.config.Constants

/** 陪伴界面里能去的页面。聊天是主页面，其余三个从顶部进入。 */
enum class CompanionPage { CHAT, CHARACTERS, MEMORY, SETTINGS, VOICE }

/**
 * 合并后的文字对话由宿主（:app）提供：新服务的会话、编辑 / 删除 / 候选回复 / 提醒都在 :app 里。
 * 参数：当前角色 id，以及一个回调——对话把「正在想 / 正在说 / 空闲」报给形象。
 */
typealias CompanionChatContent = @Composable (characterId: String, onAvatarState: (String) -> Unit) -> Unit

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
    /** 给了它，文字对话用新服务（记忆按角色隔离），旧的 LiveKit 页只保留给语音。不给就是旧行为。 */
    chatContent: CompanionChatContent? = null,
) {
    val context = LocalContext.current.applicationContext
    var page by rememberSaveable { mutableStateOf(CompanionPage.CHAT) }
    var characterId by rememberSaveable { mutableStateOf(initialCharacterId) }
    val currentCharacter by rememberUpdatedState(characterId)
    val api = remember(context, chatContent != null) {
        HttpCompanionApi(
            baseUrl = CompanionConfig.baseUrl,
            tokenProvider = { PreferenceUtils.getPrefString(context, com.openapi.common.net.http.NetConfig.tokenHeader(), null) },
            // 新服务按角色分记忆空间：管理页读写同一个空间，才能看到对话里记下的东西
            roleIdProvider = if (chatContent != null) ({ currentCharacter }) else null,
        )
    }

    val chatViewModel: CompanionViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CompanionViewModel(api = api, transport = LiveKitCompanionTransport(context)) }
        },
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // 离开聊天页就停掉收件箱轮询，回来时由 CompanionScreen 的 start() 重新拉起。
        LaunchedEffect(page) {
            if (page != CompanionPage.CHAT) chatViewModel.stopListening()
        }
        when (page) {
            CompanionPage.CHAT -> {
                TopActions(
                    onCharacters = { page = CompanionPage.CHARACTERS },
                    onMemory = { page = CompanionPage.MEMORY },
                    onSettings = { page = CompanionPage.SETTINGS },
                    onVoice = if (chatContent != null) ({ page = CompanionPage.VOICE }) else null,
                )
                if (chatContent != null) {
                    MergedChat(characterId = characterId, content = chatContent)
                } else {
                    CompanionScreen(
                        viewModel = chatViewModel,
                        characterId = characterId,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 语音通话仍走旧的 LiveKit 链路（服务端语音 worker 还没迁到新服务）：
            // 这一页的对话历史与记忆和文字对话是分开的。
            CompanionPage.VOICE -> {
                TextButton(onClick = { page = CompanionPage.CHAT }) { Text(stringResource(R.string.companion_voice_back)) }
                CompanionScreen(
                    viewModel = chatViewModel,
                    characterId = characterId,
                    modifier = Modifier.fillMaxWidth(),
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
                    onPick = {
                        characterId = it.id
                        page = CompanionPage.CHAT
                    },
                    onBack = { page = CompanionPage.CHAT },
                )
            }

            CompanionPage.MEMORY -> {
                val vm: MemoryViewModel = viewModel(
                    key = "memory-$characterId",   // 每个角色一份：切换角色后进来读的是那个角色的记忆空间
                    factory = viewModelFactory { initializer { MemoryViewModel(api) } },
                )
                val state by vm.state.collectAsStateWithLifecycle()
                MemoryScreen(
                    state = state,
                    onRetry = vm::reload,
                    onDeleteFact = vm::deleteFact,
                    onDeleteEvent = vm::deleteEvent,
                    onMarkDone = vm::markEventDone,
                    onAddEvent = vm::addEvent,
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
}

@Composable
private fun TopActions(
    onCharacters: () -> Unit,
    onMemory: () -> Unit,
    onSettings: () -> Unit,
    onVoice: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.companion_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        onVoice?.let { voice ->
            IconButton(onClick = voice) {
                Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.companion_open_voice))
            }
        }
        IconButton(onClick = onCharacters) {
            Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.companion_open_characters))
        }
        IconButton(onClick = onMemory) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.companion_open_memory))
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.companion_open_settings))
        }
    }
}

object CompanionConfig {
    /** 服务端与主站同一网关，路径由 HttpCompanionApi 拼上 /companion/... 前缀。 */
    val baseUrl: String get() = "http://${Constants.BASE_IP}"
}

/** 默认角色。角色列表选过之后以列表为准，这里只保证第一次进来有一个角色。 */
const val DEFAULT_CHARACTER_ID = "linche"

/**
 * 合并后的聊天页：上面是形象，下面是宿主提供的对话。对话把状态报给形象（想 / 说 / 空闲），
 * 形象区高度固定，对话区拿剩下的全部空间，键盘弹出时形象区被挤掉而不是挤掉输入框。
 */
@Composable
private fun MergedChat(characterId: String, content: CompanionChatContent) {
    val renderer = rememberCompanionAvatar()
    var avatarState by remember { mutableStateOf("idle") }
    RenderAvatarEffects(renderer, characterId, avatarState, emotion = null)
    Column(Modifier.fillMaxSize().imePadding()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MERGED_AVATAR_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            SimpleAvatar(renderer = renderer, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content(characterId) { avatarState = it }
        }
    }
}

private val MERGED_AVATAR_HEIGHT = 220.dp
