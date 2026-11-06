package com.openapi.ks.myapp.ui.base.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.core.design.theme.AppColors
import com.openapi.ks.chat.R

/**
 * 抽屉菜单的状态。由 [BaseDrawerActivity] 在登录态变化时写入，界面只负责渲染。
 *
 * 原 NavigationView 里 location / favorite / donation / support 以及积分说明区域在 XML 中
 * 就是隐藏的，这里不再渲染；数据仍由基类维护，便于以后重新打开。
 */
class DrawerMenuState {
    var loggedIn by mutableStateOf(false)
    var userName by mutableStateOf("")
    var loginHint by mutableStateOf("")
    /** 头像资源 id。 */
    var avatarRes by mutableStateOf(R.drawable.ic_login_default_header)
    /** 会员等级图标资源 id，0 表示不显示。 */
    var levelRes by mutableStateOf(0)
    /** 测试包下隐藏"应用推荐"。 */
    var showAppItem by mutableStateOf(true)
}

/** 菜单项点击回调；用 fun interface 以便 Java 直接传方法引用。 */
fun interface DrawerItemListener {
    fun onItem(id: Int)
}

/** 把抽屉菜单绑定到 [ComposeView]。[onItem] 收到菜单项的 R.id，[onHeaderClick] 在未登录时点头部触发。 */
fun ComposeView.bindDrawerMenu(
    state: DrawerMenuState,
    onItem: DrawerItemListener,
    onHeaderClick: Runnable,
) {
    setContent {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                DrawerMenu(state, { onItem.onItem(it) }, { onHeaderClick.run() })
            }
        }
    }
}

private data class MenuEntry(val id: Int, val icon: Int, val title: Int)

@Composable
private fun DrawerMenu(
    state: DrawerMenuState,
    onItem: (Int) -> Unit,
    onHeaderClick: () -> Unit,
) {
    val group1 = listOf(
        MenuEntry(R.id.menu_chat_session, R.drawable.ic_menu_chathistory, R.string.menu_btn_chat_session),
        MenuEntry(R.id.menu_local_music, R.drawable.ic_menu_fav, R.string.menu_btn_local_music),
        MenuEntry(R.id.menu_photo_solve, R.drawable.ic_menu_photo_solve, R.string.menu_btn_photo_solve),
    )
    val group2 = listOf(
        MenuEntry(R.id.menu_feedback, R.drawable.ic_menu_help, R.string.menu_btn_feedback),
    )
    val group3 = buildList {
        add(MenuEntry(R.id.menu_share, com.openapi.core.util.R.drawable.ic_menu_share, R.string.menu_btn_share))
        if (state.showAppItem) add(MenuEntry(R.id.menu_app, R.drawable.ic_menu_recomm, R.string.menu_btn_app))
    }
    val group4 = listOf(
        MenuEntry(R.id.menu_setting, R.drawable.ic_menu_setting, R.string.menu_btn_setting),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        DrawerHeader(state, onHeaderClick)
        HorizontalDivider(color = AppColors.Divider)
        group1.forEach { DrawerItem(it, onItem) }
        HorizontalDivider(color = AppColors.Divider)
        group2.forEach { DrawerItem(it, onItem) }
        HorizontalDivider(color = AppColors.Divider)
        group3.forEach { DrawerItem(it, onItem) }
        HorizontalDivider(color = AppColors.Divider)
        group4.forEach { DrawerItem(it, onItem) }
        if (state.loggedIn) {
            HorizontalDivider(color = AppColors.Divider)
            DrawerItem(MenuEntry(R.id.menu_louout, R.drawable.ic_menu_delete, R.string.menu_btn_logout), onItem)
        }
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun DrawerHeader(state: DrawerMenuState, onHeaderClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.loggedIn, onClick = onHeaderClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(state.avatarRes),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(8.dp))
        if (state.levelRes != 0) {
            Image(painter = painterResource(state.levelRes), contentDescription = null)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = state.userName,
            color = AppColors.Primary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = state.loginHint,
            color = AppColors.TextSecondary,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun DrawerItem(entry: MenuEntry, onItem: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItem(entry.id) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Image(painter = painterResource(entry.icon), contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(20.dp))
        Text(stringResource(entry.title), color = AppColors.TextPrimary, fontSize = 15.sp)
    }
}
