package com.openapi.feature.user.setting

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
import com.openapi.core.config.CommonConstants
import com.openapi.ads.base.AdsContext
import com.openapi.ads.analytics.MobAgent
import com.openapi.uikit.theme.AppColors
import com.openapi.uikit.theme.AppSpacing
import com.openapi.core.event.CoreEvents
import com.openapi.common.util.log.LogUtil
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.common.util.lang.StringUtils
import com.openapi.common.util.android.ToastUtil
import com.openapi.feature.user.R
import com.openapi.core.app.AppContext
import com.openapi.core.config.Constants
import com.openapi.core.update.VersionUpdater
import com.openapi.core.legacyui.app.BaseSingleActivity
import com.openapi.feature.user.setting.SettingCharacterFragment
import com.openapi.core.config.PlaybackEnginePrefs
import com.openapi.uikit.theme.AppMaterialTheme

@Composable
internal fun SettingCard(content: @Composable () -> Unit) {
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
internal fun SwitchRow(
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
internal fun ActionRow(title: String, trailing: String?, onClick: () -> Unit) {
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
internal fun FieldRow(value: String, hintRes: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(hintRes)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs),
    )
}

@Composable
internal fun SettingDivider() {
    HorizontalDivider(color = AppColors.Divider)
}
