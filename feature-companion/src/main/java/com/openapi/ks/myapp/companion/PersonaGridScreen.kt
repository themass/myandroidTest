package com.openapi.ks.myapp.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.openapi.ks.myapp.avatar.AvatarPackStore
import com.openapi.ks.myapp.avatar.AvatarPortrait

/**
 * persona 着陆页（OpenDesign 稿 01「她是谁」）：陪伴 tab 的首屏。
 *
 * 稿子的语义是「先选一个想陪你说话的人」——人格在前，形象在后。所以这一屏只有人格字段
 * （名字 / 一句话人设 / 标签 / 声音），形象的事留给下一屏（[HerListScreen]，稿 02）。
 * 双列卡格、点卡片即选定并进入对话，与稿里的「点卡片 → 02」同一步（这一版直接进 CHAT：
 * 选人格的目的是换人说话，不是看下一屏）。
 *
 * 数据仍走服务端角色列表（[CharacterListState]，由 [CharacterListViewModel] 提供），
 * 没有本地假数据；屏上该画哪一块由 [personaLandingContent] 一处决定（含未登录与
 * 加载失败的分开处理）。缩略图沿用顶栏/角色列表同一套 key 规则：素材命中才出图，
 * 没素材用名字首字——**不放占位剪影**，假图比没有图更误导（与 her 列表页同一口径）。
 */
@Composable
fun PersonaGridScreen(
    state: CharacterListState,
    /** 当前正在聊的角色：只有它带「使用中」角标。 */
    currentCharacterId: String?,
    /** 有没有登录 token：没有就别让用户去「重试」一件重试不了的事（见 [personaLandingContent]）。 */
    hasToken: Boolean,
    onRetry: () -> Unit,
    /** 去登录：由宿主接登录页；没有这个入口时退回 [onRetry]（至少给用户一条出路）。 */
    onLoginRequired: (() -> Unit)? = null,
    onPick: (CharacterSummary) -> Unit,
    /** 「呈现形象」入口：直达已有的 HERS 页（稿 02）。 */
    onOpenHers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PersonaLandingHeader(onOpenHers = onOpenHers)

        when (personaLandingContent(
            loading = state.loading,
            error = state.error,
            itemCount = state.items.size,
            hasToken = hasToken,
        )) {
            PersonaLandingContent.LOADING -> {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            }

            PersonaLandingContent.NEEDS_LOGIN -> PersonaLandingMessage(
                text = "先登录，才能看到她是谁。",
                actionLabel = "去登录",
                // 登录入口由宿主接（CompanionRoute 的 onLoginRequired）；没有就退回重试，
                // 至少给用户一条出路，而不是把话说到一半。
                onAction = onLoginRequired ?: onRetry,
                primary = true,
            )

            PersonaLandingContent.ERROR -> PersonaLandingMessage(
                text = state.error ?: "角色列表加载失败",
                actionLabel = "重试",
                onAction = onRetry,
            )

            PersonaLandingContent.EMPTY -> PersonaLandingMessage(
                text = "还没有可陪你的人。稍后再来看看。",
                actionLabel = null,
                onAction = null,
            )

            PersonaLandingContent.GRID -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.items, key = { it.id }) { character ->
                    PersonaCard(
                        character = character,
                        current = character.id == currentCharacterId,
                        onClick = { onPick(character) },
                    )
                }
            }
        }
    }
}

/** 页顶：标题 + 副标题 + 两个入口（角色人格 / 呈现形象），对应稿 01 的 tabs。 */
@Composable
private fun PersonaLandingHeader(onOpenHers: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("她是谁", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "先选一个想陪你说话的人",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 「角色人格」就是当前这一屏：不做成可点的东西，点了什么也不会发生。
            PersonaTab(text = "角色人格", selected = true, onClick = null)
            PersonaTab(text = "呈现形象", selected = false, onClick = onOpenHers)
        }
    }
}

@Composable
private fun PersonaTab(text: String, selected: Boolean, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) HerViolet else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) HerViolet.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) HerViolet else Color.Transparent,
                shape = shape,
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

/** 一张 persona 卡：缩略位 + 名字 + 一句话人设 + 标签 + 声音 pill +（仅当前）角标。 */
@Composable
private fun PersonaCard(
    character: CharacterSummary,
    current: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .then(if (current) Modifier.border(2.dp, HerViolet, shape) else Modifier)
            .clickable { onClick() }
            .padding(9.dp),
    ) {
        PersonaThumb(character = character, badge = personaCardBadge(current))
        Spacer(Modifier.height(9.dp))
        Text(
            personaDisplayName(character),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (character.tagline.isNotBlank()) {
            Text(
                character.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        personaTagsLine(character)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        personaVoicePill(character)?.let {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) { HerPill(it) }
        }
    }
}

/**
 * 缩略位：素材命中（[AvatarPackStore] 有该角色立绘）就出图，否则名字首字。
 * 角标（「使用中」）浮在右上，与稿 01 的 .corner 同一位置。
 */
@Composable
private fun PersonaThumb(character: CharacterSummary, badge: String?) {
    val context = LocalContext.current
    val store = remember { AvatarPackStore(context) }
    val portrait = remember(character.id) {
        store.avatarFor(character.id)?.let { key -> store.load(key)?.portrait }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(HerViolet.copy(alpha = 0.20f), Color(0xFFFFA6C9).copy(alpha = 0.14f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (portrait != null) {
            AvatarPortrait(
                file = portrait,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(HerViolet.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = character.name.trim().take(1).ifBlank { character.id.take(1) },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6F5BD6),
                )
            }
        }
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = HerViolet,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun PersonaLandingMessage(
    text: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    /** 主行动（去登录）用实心钮；重试这类次级动作用描边钮。 */
    primary: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            if (primary) {
                Button(onClick = onAction) { Text(actionLabel) }
            } else {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

private val previewCharacters = listOf(
    CharacterSummary(
        id = "linche",
        name = "林澈",
        tagline = "海边的她 · 安静倾听，也会认真回应",
        tags = listOf("安静", "治愈"),
        voiceAvailable = true,
        voiceLabel = "沉稳男声",
    ),
    CharacterSummary(
        id = "sudai",
        name = "苏黛",
        tagline = "把日常过成散文的温柔搭档",
        tags = emptyList(),
        voiceAvailable = false,
    ),
)

@Preview(name = "persona landing", showBackground = true, widthDp = 390)
@Composable
private fun PersonaLandingPreview() {
    MaterialTheme {
        PersonaGridScreen(
            state = CharacterListState(items = previewCharacters, total = previewCharacters.size),
            currentCharacterId = "linche",
            hasToken = true,
            onRetry = {},
            onPick = {},
            onOpenHers = {},
        )
    }
}

@Preview(name = "persona landing needs login", showBackground = true, widthDp = 390)
@Composable
private fun PersonaLandingLoginPreview() {
    MaterialTheme {
        PersonaGridScreen(
            state = CharacterListState(error = "登录已失效，请重新登录"),
            currentCharacterId = "linche",
            hasToken = false,
            onRetry = {},
            onPick = {},
            onOpenHers = {},
        )
    }
}
