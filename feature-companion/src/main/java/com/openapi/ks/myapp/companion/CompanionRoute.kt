package com.openapi.ks.myapp.companion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.openapi.core.CommonConstants
import com.openapi.core.util.PreferenceUtils
import com.openapi.feature.companion.R
import com.openapi.ks.myapp.constant.Constants

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
) {
    val context = LocalContext.current.applicationContext
    val api = remember(context) {
        HttpCompanionApi(
            baseUrl = CompanionConfig.baseUrl,
            tokenProvider = { PreferenceUtils.getPrefString(context, CommonConstants.HTTP_TOKEN_KEY, null) },
        )
    }

    var page by rememberSaveable { mutableStateOf(CompanionPage.CHAT) }
    var characterId by rememberSaveable { mutableStateOf(initialCharacterId) }

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
                )
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
