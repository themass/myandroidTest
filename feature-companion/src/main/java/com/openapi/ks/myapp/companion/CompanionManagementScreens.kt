package com.openapi.ks.myapp.companion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openapi.feature.companion.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ============================ 角色列表 ============================

@Composable
fun CharacterListScreen(
    state: CharacterListState,
    onRetry: () -> Unit,
    onPick: (CharacterSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(title = stringResource(R.string.companion_characters_title), onBack = onBack)
        when {
            state.loading && state.items.isEmpty() -> Loading()
            state.error != null && state.items.isEmpty() -> ErrorBlock(state.error, onRetry)
            state.items.isEmpty() -> EmptyBlock()
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.items, key = { it.id }) { character ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(character) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(character.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(character.tagline, style = MaterialTheme.typography.bodyMedium)
                        if (character.tags.isNotEmpty()) {
                            Text(
                                character.tags.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

// ============================ 记忆与事件 ============================

@Composable
fun MemoryScreen(
    state: MemoryState,
    onRetry: () -> Unit,
    onDeleteFact: (String) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onMarkDone: (String) -> Unit,
    onAddEvent: (title: String, atIso: String, kind: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(title = stringResource(R.string.companion_memory_title), onBack = onBack)
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.companion_tab_facts)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.companion_tab_events)) })
        }
        Spacer(Modifier.height(8.dp))
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        when {
            state.loading && state.facts.isEmpty() && state.events.isEmpty() -> Loading()
            tab == 0 -> {
                if (state.facts.isEmpty()) {
                    EmptyBlock()
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.facts, key = { it.id }) { fact ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(fact.content, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        formatShortDate(fact.createdAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { onDeleteFact(fact.id) }) {
                                    Text(stringResource(R.string.companion_delete))
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
            else -> EventsPane(
                events = state.events,
                onDelete = onDeleteEvent,
                onDone = onMarkDone,
                onAdd = onAddEvent,
            )
        }
    }
}

@Composable
private fun EventsPane(
    events: List<TimelineEventItem>,
    onDelete: (String) -> Unit,
    onDone: (String) -> Unit,
    onAdd: (title: String, atIso: String, kind: String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text(stringResource(R.string.companion_event_title_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(stringResource(R.string.companion_event_date_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val iso = localToUtcIso(date)
                        if (iso != null && title.isNotBlank()) {
                            onAdd(title, iso, "other")
                            title = ""
                            date = ""
                        }
                    },
                    enabled = title.isNotBlank() && localToUtcIso(date) != null,
                ) { Text(stringResource(R.string.companion_add_event)) }
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
        }
        if (events.isEmpty()) {
            item { EmptyBlock() }
        }
        items(events, key = { it.id }) { event ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(event.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${formatShortDate(event.at)} · ${event.status}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (event.status == "scheduled") {
                    TextButton(onClick = { onDone(event.id) }) { Text(stringResource(R.string.companion_done)) }
                }
                TextButton(onClick = { onDelete(event.id) }) { Text(stringResource(R.string.companion_delete)) }
            }
            HorizontalDivider()
        }
    }
}

// ============================ 偏好 ============================

@Composable
fun CompanionSettingsScreen(
    state: SettingsState,
    onRetry: () -> Unit,
    onChange: (Map<String, Any?>) -> Unit,
    onPauseHour: () -> Unit,
    onResume: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(title = stringResource(R.string.companion_settings_title), onBack = onBack)
        val prefs = state.prefs
        when {
            prefs == null && state.loading -> Loading()
            prefs == null -> ErrorBlock(state.error, onRetry)
            else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                SwitchRow(stringResource(R.string.companion_pref_events), prefs.eventsEnabled) {
                    onChange(mapOf("events_enabled" to it))
                }
                SwitchRow(stringResource(R.string.companion_pref_greetings), prefs.greetingsEnabled) {
                    onChange(mapOf("greetings_enabled" to it))
                }
                SwitchRow(stringResource(R.string.companion_pref_call), prefs.callEnabled) {
                    onChange(mapOf("call_enabled" to it))
                }
                Text(
                    stringResource(R.string.companion_pref_quiet, prefs.quietStart, prefs.quietEnd),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val paused = prefs.pausedUntil
                if (paused != null) {
                    Text(
                        stringResource(R.string.companion_pref_paused_until, formatShortDate(paused)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onResume) { Text(stringResource(R.string.companion_pref_resume)) }
                } else {
                    OutlinedButton(onClick = onPauseHour) { Text(stringResource(R.string.companion_pref_pause_1h)) }
                }
                if (state.saving) {
                    Text(
                        stringResource(R.string.companion_saving),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

// ============================ 公共块 ============================

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.companion_nav_back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Loading() {
    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
}

@Composable
private fun EmptyBlock() {
    Text(
        stringResource(R.string.companion_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
private fun ErrorBlock(message: String?, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message ?: stringResource(R.string.companion_error_generic), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.companion_retry)) }
    }
}

// ============================ 时间小工具 ============================

private const val UTC_ISO = "yyyy-MM-dd'T'HH:mm:ss'Z'"

/** 本地「yyyy-MM-dd HH:mm」→ UTC ISO 字符串（服务端只认带时区的时间）。解析失败返回 null。 */
internal fun localToUtcIso(input: String): String? {
    val local = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
    val parsed = runCatching { local.parse(input.trim()) }.getOrNull() ?: return null
    return SimpleDateFormat(UTC_ISO, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(parsed)
}

/** 将来的时间给 UTC ISO，用于「暂停一小时」。 */
internal fun utcIsoAfterHours(hours: Int): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.time = Date(System.currentTimeMillis() + hours * 3_600_000L)
    return SimpleDateFormat(UTC_ISO, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(cal.time)
}

/** 服务端给的 ISO 时间 → 本地「MM-dd HH:mm」。解析失败原样返回。 */
internal fun formatShortDate(iso: String): String {
    val formats = listOf("yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ssXXX")
    for (pattern in formats) {
        val date = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)
        }.getOrNull()
        if (date != null) return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(date)
    }
    return iso
}
