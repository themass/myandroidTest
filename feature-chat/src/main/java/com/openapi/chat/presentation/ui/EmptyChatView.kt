package com.openapi.chat.presentation.ui

import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.openapi.chatkit.message.rememberMessageSelection
import com.openapi.chatkit.message.MessageSelectionBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.presentation.ChatUiState
import com.openapi.chatkit.input.ChatComposer
import com.openapi.chatkit.message.ChatMessageList
import com.openapi.chatkit.message.parts.DefaultMessageText
import com.openapi.chatkit.model.ComposerAction
import com.openapi.chatkit.model.ComposerState
import com.openapi.chatkit.model.DraftItem
import com.openapi.chatkit.model.DraftType
import com.openapi.chatkit.model.MessageAction
import com.openapi.chatkit.model.Sender
import com.openapi.chatkit.model.VoiceStyle
import com.openapi.chatkit.theme.ChatKitTheme
import com.openapi.chat.domain.model.MessageKind
import com.openapi.common.design.theme.AppColors
import com.openapi.common.design.theme.AppSpacing
import com.openapi.common.design.theme.AppTypography
import com.openapi.core.media.AudioPlayer
import com.openapi.feature.chat.R
import com.openapi.roles.domain.SelectedRole
import com.openapi.roles.presentation.RoleAvatar

/**
 * 空对话：当前角色的头像、名字，和几句可以直接点的开场白。
 * 点开场白只是填进输入框，不会自己发出去，用户可以先改。
 */
@Composable
internal fun EmptyChat(identity: AssistantIdentity, onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(AppSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RoleAvatar(emoji = identity.emoji, colorArgb = identity.colorArgb, size = 72, imageRes = identity.avatarRes)
        Spacer(Modifier.height(AppSpacing.md))
        Text(
            identity.name,
            fontSize = AppTypography.screenTitle,
            fontWeight = AppTypography.titleWeight,
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            stringResource(R.string.chat_empty_hint),
            fontSize = AppTypography.body,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppSpacing.lg))
        listOf(
            stringResource(R.string.chat_suggest_intro),
            stringResource(R.string.chat_suggest_help),
            stringResource(R.string.chat_suggest_example),
        ).forEach { text ->
            SuggestionChip(
                onClick = { onSuggestion(text) },
                label = { Text(text) },
                modifier = Modifier.padding(vertical = AppSpacing.xs),
            )
        }
    }
}
