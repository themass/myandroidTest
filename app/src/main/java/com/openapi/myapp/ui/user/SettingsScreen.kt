package com.openapi.myapp.ui.user

import androidx.compose.foundation.background
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.openapi.ks.free1.R

/** 设置页的显示状态。Java 侧负责填充；Compose 切换开关时会同步更新这里的字段。 */
class SettingsUi {
    var notify by mutableStateOf(true)
    var listCard by mutableStateOf(true)
    var pulse by mutableStateOf(true)
    var globe by mutableStateOf(true)
    var newHome by mutableStateOf(true)
    var protonHome by mutableStateOf(false)
    var timeUse by mutableStateOf("")
    var traffic by mutableStateOf("")
    var score by mutableStateOf("")
    var version by mutableStateOf("")
    var cache by mutableStateOf("")
    var protocol by mutableStateOf("")
    var splitTunnel by mutableStateOf("")
    var killSwitch by mutableStateOf("")
    var vless by mutableStateOf("")
}

/** 界面回调：开关写入偏好，行点击交给 SettingActivity 处理。 */
interface SettingsActions {
    fun onToggle(key: String, value: Boolean)
    fun onRow(key: String)
}

private val SetBg = Color(0xFF1C1B24)
private val SetCard = Color(0xFF292733)
private val SetDivider = Color(0xFF3F3B4C)
private val SetText = Color(0xFFE8E8EE)
private val SetTextSecondary = Color(0xFFA7A4B5)
private val SetAccent = Color(0xFF6D4AFF)

@Composable
fun SettingsScreen(ui: SettingsUi, actions: SettingsActions) {
    MaterialTheme(colorScheme = darkColorScheme(background = SetBg, surface = SetCard, primary = SetAccent)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SetBg)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SettingsGroup(R.string.group_features) {
                ValueRow(R.string.setting_kill_switch, ui.killSwitch) { actions.onRow("kill_switch") }
                Divider()
            }

            SettingsGroup(R.string.group_general) {
                SwitchRow(R.string.menu_btn_notify, ui.notify) {
                    ui.notify = it
                    actions.onToggle("notify", it)
                }
                Divider()
                SwitchRow(R.string.setting_list_card_style, ui.listCard) {
                    ui.listCard = it
                    actions.onToggle("list_card", it)
                }
                Divider()
                ValueRow(R.string.menu_btn_cache, ui.cache) { actions.onRow("cache") }
                Divider()
                ValueRow(R.string.menu_btn_version, ui.version) { actions.onRow("version") }
            }

            SettingsGroup(R.string.group_appearance) {
                SwitchRow(R.string.setting_pulse_style, ui.pulse) {
                    ui.pulse = it
                    actions.onToggle("pulse", it)
                }
                Divider()
                SwitchRow(R.string.setting_globe_view, ui.globe) {
                    ui.globe = it
                    actions.onToggle("globe", it)
                }
                Divider()
                SwitchRow(R.string.setting_new_home, ui.newHome) {
                    ui.newHome = it
                    actions.onToggle("new_home", it)
                }
                Divider()
                SwitchRow(R.string.setting_proton_home, ui.protonHome) {
                    ui.protonHome = it
                    actions.onToggle("proton_home", it)
                }
            }

            SettingsGroup(R.string.group_account) {
                ValueRow(R.string.setting_reward, "") { actions.onRow("reward") }
                Divider()
                ValueRow(R.string.menu_btn_timeuse, ui.timeUse) { actions.onRow("timeuse") }
                Divider()
                ValueRow(R.string.menu_btn_networking, ui.traffic) { actions.onRow("traffic") }
                Divider()
                ValueRow(R.string.setting_change_password, "") { actions.onRow("change_password") }
            }

            SettingsGroup(R.string.group_support) {
                ValueRow(R.string.support_about, "") { actions.onRow("about") }
                Divider()
                ValueRow(R.string.support_feedback, "") { actions.onRow("feedback") }
                Divider()
                ValueRow(R.string.support_contact, "") { actions.onRow("contact") }
                Divider()
                ValueRow(R.string.privacy_policy, "") { actions.onRow("privacy") }
                Divider()
                ValueRow(R.string.menu_btn_share, "") { actions.onRow("share") }
            }
        }
    }
}

@Composable
private fun SettingsGroup(titleRes: Int, content: @Composable () -> Unit) {
    Text(
        text = stringResource(titleRes).uppercase(),
        color = SetTextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SetCard),
    ) {
        content()
    }
}

@Composable
private fun ValueRow(labelRes: Int, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            color = SetText,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Text(text = value, color = SetTextSecondary, fontSize = 14.sp)
            Spacer(Modifier.size(8.dp))
        }
        Text(text = "›", color = SetTextSecondary, fontSize = 18.sp)
    }
}

@Composable
private fun SwitchRow(labelRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            color = SetText,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = SetAccent),
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(SetDivider),
    )
}

/** 供 Java 调用：把设置页 Compose 界面绑定到 ComposeView。 */
fun bindSettingsScreen(view: ComposeView, ui: SettingsUi, actions: SettingsActions) {
    view.setContent { SettingsScreen(ui, actions) }
}
