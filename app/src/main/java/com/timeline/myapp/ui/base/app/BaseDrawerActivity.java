package com.timeline.myapp.ui.base.app;

import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.ads.base.AdsManager;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.R;
import com.timeline.myapp.bean.vo.UserInfoVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.BaseService;
import com.timeline.myapp.data.LocationUtil;
import com.timeline.myapp.data.UserLoginUtil;
import com.timeline.myapp.data.VersionUpdater;
import com.timeline.myapp.data.config.LocationChooseEvent;
import com.timeline.myapp.data.config.StateUseEvent;
import com.timeline.myapp.data.config.UserLoginEvent;
import com.timeline.myapp.ui.feedback.IWannaFragment;
import com.timeline.myapp.ui.fragment.AppListFragment;
import com.timeline.myapp.ui.fragment.FavoriteFragment;
import com.timeline.myapp.ui.fragment.LocationChooseFragment;
import com.timeline.myapp.ui.user.LoginActivity;
import com.timeline.myapp.ui.user.SettingActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.BindView;

/**
 * Created by Miroslaw Stanek on 15.07.15.
 */
public class BaseDrawerActivity extends BaseToolBarActivity {
    @Nullable
    @BindView(R.id.drawerLayout)
    public DrawerLayout drawerLayout;
    @Nullable
    @BindView(R.id.nv_drawer)
    NavigationView nvDrawer;
    View headerView;
    LinearLayout llLoginMenuHeader;
    TextView tvMenuUserName;
    TextView tvMenuUserLogin;
    ImageView ivAvatar;
    ImageView ivLevel;
    MenuItem miLogout;
    MenuItem miLocation;
    MenuItem miSetting;
    MenuItem miFavorite;
    MenuItem miApp;
    BaseService baseService;

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
        miLogout = nvDrawer.getMenu().findItem(R.id.menu_louout);
        miLocation = nvDrawer.getMenu().findItem(R.id.menu_location);
        miSetting = nvDrawer.getMenu().findItem(R.id.menu_setting);
        miFavorite = nvDrawer.getMenu().findItem(R.id.menu_favorite);
        headerView = nvDrawer.getHeaderView(0);
        llLoginMenuHeader = (LinearLayout) headerView.findViewById(R.id.ll_menu_headview);
        tvMenuUserName = (TextView) headerView.findViewById(R.id.tv_menu_username);
        tvMenuUserLogin = (TextView) headerView.findViewById(R.id.tv_menu_login);
        ivAvatar = (ImageView) headerView.findViewById(R.id.iv_avatar);
        ivLevel = (ImageView) headerView.findViewById(R.id.iv_level);
        miApp = nvDrawer.getMenu().findItem(R.id.menu_app);
        nvDrawer.setItemIconTintList(null);
        setUpVersion();
        setUpUserMenu();
        setUpLocation();
        baseService = new BaseService();
        baseService.setup(this);
    }

    private void setUpLocation() {
        boolean flag = PreferenceUtils.getPrefBoolean(this, Constants.LOCATION_FLAG, false);
        if (!flag) {
            miLocation.setIcon(R.drawable.ic_menu_location_flag);
        } else {
            miLocation.setIcon(R.drawable.ic_menu_location);
        }
        miLocation.setTitle(LocationUtil.getSelectName(this));
    }

    private void setUpVersion() {
        VersionUpdater.checkUpdate(BaseDrawerActivity.this, false);
    }

    private void setUpUserMenu() {
        UserInfoVo vo = UserLoginUtil.getUserCache();
        if (vo != null) {
            miLogout.setVisible(true);
            llLoginMenuHeader.setEnabled(false);
            tvMenuUserName.setText(vo.name);
            tvMenuUserLogin.setText(R.string.menu_btn_login_ready);
            if (Constants.SEX_M.equals(vo.sex)) {
                ivAvatar.setImageResource(R.drawable.ic_default_nan);
            } else {
                ivAvatar.setImageResource(R.drawable.ic_default_nv);
            }
            ivLevel.setVisibility(View.VISIBLE);
            if (Constants.UserLevel.LEVEL_FREE == vo.level) {
                ivLevel.setImageResource(R.drawable.ic_level_free);
            } else if (Constants.UserLevel.LEVEL_VIP == vo.level) {
                ivLevel.setImageResource(R.drawable.ic_level_vip);
            }else if (Constants.UserLevel.LEVEL_VIP2 == vo.level) {
                ivLevel.setImageResource(R.drawable.ic_level_vip2);
            }
        } else {
            miLogout.setVisible(false);
            llLoginMenuHeader.setEnabled(true);
            tvMenuUserName.setText(R.string.menu_btn_name_default);
            tvMenuUserLogin.setText(R.string.menu_btn_login);
            ivLevel.setVisibility(View.GONE);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UserLoginEvent event) {
        setUpUserMenu();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(LocationChooseEvent event) {
        setUpLocation();
        setupLocationIcon();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(StateUseEvent event) {
        PreferenceUtils.setPrefObj(this, Constants.USER_STATUS, event.stateUse);
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
                    if (!closeDrawer()) {
                        try {
                            drawerLayout.openDrawer(Gravity.LEFT);
                        }catch (Throwable e){
                            LogUtil.e(e);
                        }
                    }
                }
            });
        }
        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {

            }

            @Override
            public void onDrawerOpened(View drawerView) {
            }

            @Override
            public void onDrawerClosed(View drawerView) {

            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        });
        nvDrawer.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                String name = "未知";
                if (item.getItemId() == R.id.menu_louout) {
                    name = "登出";
                    logout(item);
                } else if (item.getItemId() == R.id.menu_location) {
                    name = "网络选择";
                    LocationChooseFragment.startFragment(BaseDrawerActivity.this);
                } else if (item.getItemId() == R.id.menu_feedback) {
                    name = "反馈";
                    IWannaFragment.startFragment(BaseDrawerActivity.this);
                } else if (item.getItemId() == R.id.menu_setting) {
                    name = "设置";
                    startActivity(SettingActivity.class);
                }  else if (item.getItemId() == R.id.menu_favorite) {
                    name = "收藏夹";
                    FavoriteFragment.startFragment(BaseDrawerActivity.this);
                }else if (item.getItemId() == R.id.menu_support) {
                    name = "支持作者";
                    adsOffers();
                    ToastUtil.showShort(R.string.support_info);
                } else if (item.getItemId() == R.id.menu_app) {
                    name = "应用推荐";
                    AppListFragment.startFragment(BaseDrawerActivity.this);
                }
                MobAgent.onEventMenu(BaseDrawerActivity.this, name);
                return false;
            }
        });
    }

    private void adsOffers(){
        AdsManager.getInstans().offerAds(this);
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
