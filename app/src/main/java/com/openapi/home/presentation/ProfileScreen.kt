package com.openapi.home.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.openapi.core.design.theme.AppColors
import com.openapi.home.HomeDependencies
import com.openapi.home.domain.ProfileAction
import com.openapi.home.domain.ProfileEntry
import com.openapi.home.domain.ProfileInfo

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entries = remember { HomeDependencies.require() }
    var info by remember { mutableStateOf(entries.profile(context)) }
    // 从登录页、设置页回来时刷新登录态
    LifecycleResumeEffect(Unit) {
        info = entries.profile(context)
        onPauseOrDispose { }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(AppColors.Surface)
            .verticalScroll(rememberScrollState()),
    ) {
        ProfileHeader(info) { entries.perform(context, ProfileAction.Login) }
        HorizontalDivider(color = AppColors.Divider)
        info.entries.forEach { entry ->
            ProfileRow(entry) {
                entries.perform(context, entry.action)
                info = entries.profile(context)
            }
        }
    }
}

@Composable
internal fun ProfileHeader(info: ProfileInfo, onLoginClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !info.loggedIn, onClick = onLoginClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(info.avatarRes), contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.width(8.dp))
        if (info.levelRes != 0) {
            Image(painterResource(info.levelRes), contentDescription = null)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            info.name,
            color = AppColors.Primary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Text(info.hint, color = AppColors.TextSecondary, fontSize = 14.sp)
    }
}

@Composable
internal fun ProfileRow(entry: ProfileEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(entry.iconRes), contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(20.dp))
        Text(stringResource(entry.titleRes), color = AppColors.TextPrimary, fontSize = 15.sp)
    }
}
