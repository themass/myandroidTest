package com.openapi.ks.myapp.ui.base.app;

import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;

import android.content.Intent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.compose.ui.platform.ComposeView;

import com.openapi.core.CommonConstants;
import com.openapi.core.event.UserNeedLogin;
import com.openapi.core.util.PreferenceUtils;
import com.openapi.core.util.StringUtils;
import com.openapi.core.util.DateUtils;
import com.openapi.core.util.LogUtil;
import com.openapi.core.util.ShareUtil;
import com.openapi.core.util.SystemUtils;
import com.openapi.core.util.ToastUtil;
import com.openapi.core.ads.yewu.um.MobAgent;
import com.openapi.ks.chat.R;
import com.openapi.ks.chatfree.ui.main.MainFragmentViewPage;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.vo.UserInfoVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.core.net.BaseService;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.ks.myapp.data.VersionUpdater;
import com.openapi.ks.myapp.data.config.StateUseEvent;
import com.openapi.ks.myapp.data.config.UserLoginEvent;
import com.openapi.ks.myapp.data.config.VipDescEvent;
import com.openapi.ks.myapp.ui.base.WebViewActivity;
import com.openapi.ks.myapp.ui.feedback.IWannaFragment;
import com.openapi.ks.myapp.ui.fragment.AppListFragment;
import com.openapi.ks.myapp.ui.user.LoginActivity;
import com.openapi.ks.myapp.ui.user.SettingActivity;

import com.aimath.paizhao.PhotoSolveActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Date;

/**
 * 带抽屉菜单的页面基类。
 *
 * 菜单内容由 Compose 绘制（见 [DrawerMenuCompose]），这里只维护登录态并把它写入 [DrawerMenuState]，
 * 菜单点击统一走 [onDrawerItemClick]。
 */
public class BaseDrawerActivity extends BaseToolBarActivity {
    @Nullable
    public DrawerLayout drawerLayout;

    private ViewGroup drawerContentRoot;
    private ComposeView drawerMenu;
    private final DrawerMenuState drawerState = new DrawerMenuState();
    public BaseService baseService;

    @Override
    protected void bindBaseViews() {
        super.bindBaseViews();
        drawerLayout = findViewById(R.id.drawerLayout);
        drawerContentRoot = findViewById(R.id.fl_content_root);
        drawerMenu = findViewById(R.id.drawer_menu_compose);
    }

    @Override
    protected ViewGroup contentContainer() {
        return drawerContentRoot;
    }

    public void login(View view) {
        startActivity(LoginActivity.class);
    }

    @Override
    public void setContentView(int layoutResID) {
        LogUtil.i("setContentView   " + "drawer");
        super.setContentViewWithoutInject(R.layout.menu_drawer);
        ViewGroup viewGroup = (ViewGroup) findViewById(R.id.fl_content_root);
        LayoutInflater.from(this).inflate(layoutResID, viewGroup, true);
        bindViews();
        setupToolbar();
        DrawerMenuComposeKt.bindDrawerMenu(
                drawerMenu,
                drawerState,
                this::onDrawerItemClick,
                () -> login(null));
        setUpVersion();
        setUpUserMenu();
        updateAppItemVisibility();
        baseService = new BaseService();
        baseService.setup(this);
    }

    /** 测试包下隐藏"应用推荐"（原来的 NavigationView 也是这样处理的）。 */
    private void updateAppItemVisibility() {
        drawerState.setShowAppItem(!MyApplication.isTemp);
    }

    private void setUpVersion() {
        VersionUpdater.checkUpdate(BaseDrawerActivity.this, false);
    }

    private void setUpUserMenu() {
        UserInfoVo vo = UserLoginUtil.getUserCache();
        if (vo != null) {
            drawerState.setLoggedIn(true);
            drawerState.setUserName(vo.name);
            drawerState.setLoginHint(getString(R.string.menu_btn_login_ready));
            drawerState.setAvatarRes(Constants.SEX_M.equals(vo.sex) ? R.drawable.ic_default_nan : R.drawable.ic_default_nv);
            drawerState.setLevelRes(levelIconRes(vo.level));
        } else {
            drawerState.setLoggedIn(false);
            drawerState.setUserName(getString(R.string.menu_btn_name_default));
            drawerState.setLoginHint(getString(R.string.menu_btn_login));
            drawerState.setAvatarRes(R.drawable.ic_login_default_header);
            drawerState.setLevelRes(0);
        }
    }

    private int levelIconRes(int level) {
        if (Constants.UserLevel.LEVEL_FREE == level) return R.drawable.ic_level_free;
        if (Constants.UserLevel.LEVEL_VIP == level) return R.drawable.ic_level_vip;
        if (Constants.UserLevel.LEVEL_VIP2 == level) return R.drawable.ic_level_vip2;
        if (Constants.UserLevel.LEVEL_VIP3 == level) return R.drawable.ic_level_vip3;
        if (Constants.UserLevel.LEVEL_VIP4 == level) return R.drawable.ic_level_vip4;
        return 0;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UserLoginEvent event) {
        setUpUserMenu();
        updateAppItemVisibility();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UserNeedLogin event) {
        UserLoginUtil.logout(this);
        startActivity(LoginActivity.class);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(StateUseEvent event) {
        PreferenceUtils.setPrefObj(this, Constants.USER_STATUS, event.stateUse);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(VipDescEvent event) {
        // 积分/会员说明原先在抽屉里隐藏显示，这里只保留日志
        LogUtil.i(event.stateUse.desc);
    }

    public void onAbout(View view) {
        String url = Constants.ABOUT;
        if (SystemUtils.isZH(this)) {
            url = Constants.ABOUT_ZH;
        }
        url = url + "?" + DateUtils.format(new Date(), DateUtils.DATE_FORMAT_MM);
        WebViewActivity.startWebViewActivity(this, url, getString(R.string.menu_btn_about), false, false, null);
        PreferenceUtils.setPrefBoolean(this, Constants.ABOUT_FIRST, true);
        MobAgent.onEventMenu(this, "关于");
    }

    public void logout(MenuItem item) {
        baseService.postData(Constants.getUrl(Constants.API_LOGOUT_URL), null, null, null, null, null);
        UserLoginUtil.logout(this);
    }

    public boolean closeDrawer() {
        if (drawerLayout.isDrawerOpen(Gravity.LEFT)) {
            drawerLayout.closeDrawer(Gravity.LEFT);
            return true;
        }
        return false;
    }

    @Override
    protected void setupToolbar() {
        super.setupToolbar();
        if (getToolbar() != null) {
            getToolbar().setNavigationIcon(R.drawable.ic_menu_drawer);
            getToolbar().setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (!closeDrawer()) {
                            drawerLayout.openDrawer(Gravity.LEFT);
                        }
                    } catch (Exception e) {
                        LogUtil.e(e);
                    }
                }
            });
        }
    }

    /** 菜单项点击：先收起抽屉，再按 id 分发（与原 NavigationView 的行为一致）。 */
    void onDrawerItemClick(int itemId) {
        drawerLayout.closeDrawer(Gravity.LEFT);
        String name = "未知";
        if (itemId == R.id.menu_louout) {
            name = "登出";
            logout(null);
        } else if (itemId == R.id.menu_feedback) {
            name = "反馈";
            IWannaFragment.startFragment(BaseDrawerActivity.this);
        } else if (itemId == R.id.menu_setting) {
            name = "设置";
            startActivity(SettingActivity.class);
        } else if (itemId == R.id.menu_app) {
            name = "应用推荐";
            if (UserLoginUtil.isVIP2()) {
                Intent intent = new Intent(BaseDrawerActivity.this, MainFragmentViewPage.class);
                startActivity(intent);
            } else {
                AppListFragment.startFragment(BaseDrawerActivity.this);
            }
        } else if (itemId == R.id.menu_share) {
            showShare();
            name = "分享";
        } else if (itemId == R.id.menu_chat_session) {
            name = "新建对话";
            startActivity(new Intent(BaseDrawerActivity.this, com.openapi.chat.ChatActivity.class));
        } else if (itemId == R.id.menu_photo_solve) {
            name = "拍照解题";
            PhotoSolveActivity.Companion.start(BaseDrawerActivity.this);
        } else if (itemId == R.id.menu_local_music) {
            name = "本地音乐";
            startActivity(new Intent(BaseDrawerActivity.this,
                    com.openapi.retromusic.activities.MainActivity.class));
        }
        MobAgent.onEventMenu(BaseDrawerActivity.this, name);
    }

    public void showShare() {
        String url = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.D_URL, null);
        if (!StringUtils.hasText(url)) {
            url = CommonConstants.DEFAULT_REFERER;
        }
        ShareUtil util = new ShareUtil(this);
        util.shareText(null, null, url + " 爱Freedom，精彩你的生活", "爱Freedom", "精彩你的生活");
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobAgent.onPauseForFragmentActiviy(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobAgent.onResumeForFragmentActiviy(this);
    }
}
