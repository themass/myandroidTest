package com.openapi.ks.myapp.ui.user

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.core.CommonConstants
import com.openapi.core.ads.yewu.ads.base.AdsContext
import com.openapi.core.ads.yewu.um.MobAgent
import com.openapi.core.design.theme.AppColors
import com.openapi.core.design.theme.AppSpacing
import com.openapi.core.util.EventBusUtil
import com.openapi.core.util.LogUtil
import com.openapi.core.util.PreferenceUtils
import com.openapi.core.util.ShareUtil
import com.openapi.core.util.StringUtils
import com.openapi.core.util.ToastUtil
import com.openapi.feature.user.R
import com.openapi.core.app.AppContext
import com.openapi.ks.myapp.constant.Constants
import com.openapi.ks.myapp.data.VersionUpdater
import com.openapi.ks.myapp.data.config.PlayCoreEvent
import com.openapi.ks.myapp.data.config.TabChangeEvent
import com.openapi.ks.myapp.ui.base.app.BaseSingleActivity
import com.openapi.ks.myapp.ui.fragment.SettingCharacterFragment
import com.openapi.ks.myapp.video.PlaybackEngineHelper
import com.openapi.core.design.theme.AppMaterialTheme

/**
 * 设置页（Compose 内容区）。
 *
 * 外壳仍是 [BaseSingleActivity]：标题栏和底部横幅广告沿用基类，不改变广告行为。
 * 设置项的读写、副作用（事件、Toast、埋点）与原 Java 实现一一对应。
 */
class SettingActivity : BaseSingleActivity() {

    /** 需要跨页面生命周期保存的字段（onPause 时写回）。 */
    private lateinit var form: SettingsForm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        form = SettingsForm(this)
        setContentView(R.layout.layout_setting)
        setToolbarTitle(R.string.setting, true)
        hidenAds()
    }

    override fun onBindContent(container: ViewGroup) {
        super.onBindContent(container)
        container.findViewById<ComposeView>(R.id.compose_setting).setContent {
            AppMaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Surface) {
                    SettingScreen(
                        form = form,
                        onShowVersion = {
                            VersionUpdater.checkUpdate(this, true)
                            MobAgent.onEventMenu(this, "版本")
                        },
                        onShare = {
                            showShare()
                            MobAgent.onEventMenu(this, "分享")
                        },
                        onOpenCharacter = {
                            SettingCharacterFragment.startFragment(this)
                            MobAgent.onEventMenu(this, "设置人设")
                        },
                        onSaveCharacter = {
                            PreferenceUtils.setPrefString(AppContext.get(), Constants.MY_SETTING, form.character)
                            form.saveVolcano(this)
                            MobAgent.onEventMenu(this, "设置人设")
                        },
                    )
                }
            }
        }
    }

    override fun onPause() {
        if (::form.isInitialized) form.saveVolcano(this)
        super.onPause()
    }

    fun showShare() {
        var url = PreferenceUtils.getPrefString(AppContext.get(), Constants.D_URL, null)
        if (!StringUtils.hasText(url)) {
            url = com.openapi.core.net.NetConfig.defaultReferer()
        }
        ShareUtil(this).shareText(null, null, "$url 爱Freedom，精彩你的生活", "爱Freedom", "精彩你的生活")
    }

    override fun needShow(): Boolean = true

    override fun enableSliding(): Boolean = true

    override fun getBannerCategrey(): AdsContext.Categrey = AdsContext.Categrey.CATEGREY_VPN2
}

/** 文本类设置的承载对象：保存在 Activity 上，生命周期内保持，onPause 时写回偏好。 */
class SettingsForm(context: Context) {
    var character by mutableStateOf(
        PreferenceUtils.getPrefString(AppContext.get(), Constants.MY_SETTING, "").orEmpty(),
    )
    var volcanoAppKey by mutableStateOf(PreferenceUtils.getPrefString(context, Constants.VOLCANO_ASR_APP_KEY, "").orEmpty())
    var volcanoAccessKey by mutableStateOf(PreferenceUtils.getPrefString(context, Constants.VOLCANO_ASR_ACCESS_KEY, "").orEmpty())
    var volcanoResourceId by mutableStateOf(
        PreferenceUtils.getPrefString(context, Constants.VOLCANO_ASR_RESOURCE_ID, "volc.bigasr.sauc.duration").orEmpty(),
    )
    var volcanoCluster by mutableStateOf(
        PreferenceUtils.getPrefString(context, Constants.VOLCANO_ASR_CLUSTER, "volcengine_input_common").orEmpty(),
    )
    var volcanoUseV3 by mutableStateOf(PreferenceUtils.getPrefBoolean(context, Constants.VOLCANO_ASR_USE_V3, false))

    fun saveVolcano(context: Context) {
        PreferenceUtils.setPrefString(context, Constants.VOLCANO_ASR_APP_KEY, volcanoAppKey.trim())
        PreferenceUtils.setPrefString(context, Constants.VOLCANO_ASR_ACCESS_KEY, volcanoAccessKey.trim())
        PreferenceUtils.setPrefString(context, Constants.VOLCANO_ASR_RESOURCE_ID, volcanoResourceId.trim())
        PreferenceUtils.setPrefString(context, Constants.VOLCANO_ASR_CLUSTER, volcanoCluster.trim())
        PreferenceUtils.setPrefBoolean(context, Constants.VOLCANO_ASR_USE_V3, volcanoUseV3)
    }
}

@Composable
private fun SettingScreen(
    form: SettingsForm,
    onShowVersion: () -> Unit,
    onShare: () -> Unit,
    onOpenCharacter: () -> Unit,
    onSaveCharacter: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = AppContext.get()

    // 布尔类设置：读取当前偏好，切换时写回并执行原有副作用
    var playVideo by remember { mutableStateOf(PreferenceUtils.getPrefBoolean(context, Constants.PLAYVIDEO_SWITCH, true)) }
    var listVideo by remember { mutableStateOf(PreferenceUtils.getPrefBoolean(context, Constants.LISTVIDEO_TINY_SWITCH, true)) }
    var area by remember { mutableStateOf(PreferenceUtils.getPrefBoolean(context, Constants.AREA_SWITCH, true)) }
    var areaMi by remember { mutableStateOf(PreferenceUtils.getPrefBoolean(context, Constants.AREA_MI_SWITCH, false)) }
    var sound by remember { mutableStateOf(PreferenceUtils.getPrefBoolean(context, Constants.SOUND_SWITCH, true)) }
    var musicUiAuxio by remember { mutableStateOf(PreferenceUtils.getPrefBoolean(context, Constants.MUSIC_UI_AUXIO_STYLE, false)) }
    var picShow by remember { mutableStateOf(PreferenceUtils.getPrefBoolean(context, Constants.VIDEO_PIC_SWITCH, true)) }
    var tts by remember { mutableStateOf(PreferenceUtils.getPrefBoolean(app, CommonConstants.TTS_OPEN, false)) }
    var engine by remember { mutableStateOf(PlaybackEngineHelper.getEngine(context)) }
    var engineMenuOpen by remember { mutableStateOf(false) }
    val engineNames = stringArrayResource(R.array.playback_engine_names)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        SettingCard {
            SwitchRow(stringResource(R.string.playvideo_setting), playVideo) {
                playVideo = it
                PreferenceUtils.setPrefBoolean(context, Constants.PLAYVIDEO_SWITCH, it)
                LogUtil.i("swPlayCore: $it")
                EventBusUtil.getEventBus().post(TabChangeEvent())
            }
            SettingDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.playcore_setting), modifier = Modifier.weight(1f), fontSize = 15.sp, color = AppColors.TextPrimary)
                Box {
                    TextButton(onClick = { engineMenuOpen = true }) {
                        Text(engineNames.getOrElse(engine) { "" }, color = AppColors.Primary)
                    }
                    DropdownMenu(expanded = engineMenuOpen, onDismissRequest = { engineMenuOpen = false }) {
                        engineNames.forEachIndexed { index, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    engineMenuOpen = false
                                    if (index == engine) return@DropdownMenuItem
                                    engine = index
                                    PlaybackEngineHelper.setEngine(context, index)
                                    LogUtil.i("PLAYBACK_ENGINE: $index")
                                    ToastUtil.showShort(
                                        context.getString(
                                            R.string.playback_engine_toast_fmt,
                                            PlaybackEngineHelper.engineLabel(context, index),
                                        ),
                                    )
                                    EventBusUtil.getEventBus().post(PlayCoreEvent())
                                },
                            )
                        }
                    }
                }
            }
            SettingDivider()
            SwitchRow(stringResource(R.string.area_setting), area) {
                area = it
                PreferenceUtils.setPrefBoolean(context, Constants.AREA_SWITCH, it)
                LogUtil.i("swArea: $it")
                EventBusUtil.getEventBus().post(TabChangeEvent())
            }
            SettingDivider()
            SwitchRow(stringResource(R.string.area_mi), areaMi) {
                areaMi = it
                PreferenceUtils.setPrefBoolean(context, Constants.AREA_MI_SWITCH, it)
                LogUtil.i("AREA_MI_SWITCH: $it")
            }
            SettingDivider()
            SwitchRow(stringResource(R.string.sound_setting), sound) {
                sound = it
                PreferenceUtils.setPrefBoolean(context, Constants.SOUND_SWITCH, it)
                LogUtil.i("SOUND_SWITCH: $it")
            }
            SettingDivider()
            SwitchRow(
                title = stringResource(R.string.music_ui_use_auxio_title),
                checked = musicUiAuxio,
                hint = stringResource(R.string.music_ui_use_auxio_hint),
            ) {
                musicUiAuxio = it
                PreferenceUtils.setPrefBoolean(context, Constants.MUSIC_UI_AUXIO_STYLE, it)
                LogUtil.i("MUSIC_UI_AUXIO_STYLE: $it")
                ToastUtil.showShort(
                    if (it) R.string.music_ui_auxio_enabled_toast else R.string.music_ui_auxio_disabled_toast,
                )
            }
            SettingDivider()
            SwitchRow(stringResource(R.string.sound_picshow), picShow) {
                picShow = it
                PreferenceUtils.setPrefBoolean(context, Constants.VIDEO_PIC_SWITCH, it)
                LogUtil.i("VIDEO_PIC_SWITCH: $it")
            }
            SettingDivider()
            SwitchRow(stringResource(R.string.list_video_tiny_need), listVideo) {
                listVideo = it
                PreferenceUtils.setPrefBoolean(context, Constants.LISTVIDEO_TINY_SWITCH, it)
                LogUtil.i("LISTVIDEO_TINY_SWITCH: $it")
                EventBusUtil.getEventBus().post(TabChangeEvent())
            }
        }

        SettingCard {
            ActionRow(stringResource(R.string.menu_btn_version), VersionUpdater.getVersion(), onShowVersion)
            SettingDivider()
            ActionRow(stringResource(R.string.menu_btn_share), null, onShare)
            SettingDivider()
            SwitchRow(stringResource(R.string.use_tts), tts) {
                tts = it
                PreferenceUtils.setPrefBoolean(app, CommonConstants.TTS_OPEN, it)
                LogUtil.i("TTS_OPEN: $it")
            }
        }

        SettingCard {
            Text(
                stringResource(R.string.volcano_asr_setting_title),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = AppColors.TextPrimary,
            )
            FieldRow(form.volcanoAppKey, R.string.volcano_asr_app_key_hint) { form.volcanoAppKey = it }
            FieldRow(form.volcanoAccessKey, R.string.volcano_asr_access_key_hint) { form.volcanoAccessKey = it }
            FieldRow(form.volcanoResourceId, R.string.volcano_asr_resource_id_hint) { form.volcanoResourceId = it }
            FieldRow(form.volcanoCluster, R.string.volcano_asr_cluster_hint) { form.volcanoCluster = it }
            SwitchRow(stringResource(R.string.volcano_asr_use_v3), form.volcanoUseV3) { form.volcanoUseV3 = it }
        }

        Spacer(Modifier.height(AppSpacing.xl))
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.SurfaceVariant)
            .padding(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        content()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    hint: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = AppColors.TextPrimary)
            if (hint != null) {
                Text(hint, fontSize = 11.sp, color = AppColors.TextSecondary)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(title: String, trailing: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacing.md),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 15.sp, color = AppColors.TextPrimary)
        if (!trailing.isNullOrEmpty()) {
            Text(trailing, fontSize = 13.sp, color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun FieldRow(value: String, hintRes: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(hintRes)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs),
    )
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(color = AppColors.Divider)
}
