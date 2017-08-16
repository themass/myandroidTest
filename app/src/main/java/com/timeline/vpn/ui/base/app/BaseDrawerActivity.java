package com.timeline.vpn.ui.base.app;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.sspacee.common.util.DateUtils;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.common.util.StringUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.yewu.net.HttpDNSUtil;
import com.sspacee.yewu.net.request.CommonResponse;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.R;
import com.timeline.vpn.base.MyApplication;
import com.timeline.vpn.bean.vo.StateUseVo;
import com.timeline.vpn.bean.vo.UserInfoVo;
import com.timeline.vpn.bean.vo.VersionVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.LocationUtil;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.data.VersionUpdater;
import com.timeline.vpn.data.config.LocationChooseEvent;
import com.timeline.vpn.data.config.StateUseEvent;
import com.timeline.vpn.data.config.UserLoginEvent;
import com.timeline.vpn.service.LogUploadService;
import com.timeline.vpn.ui.base.WebViewActivity;
import com.timeline.vpn.ui.feedback.IWannaFragment;
import com.timeline.vpn.ui.fragment.LocationChooseFragment;
import com.timeline.vpn.ui.user.LoginActivity;
import com.timeline.vpn.ui.user.SettingActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Date;

import butterknife.Bind;

import static com.sspacee.common.util.PreferenceUtils.getPrefBoolean;

/**
 * Created by Miroslaw Stanek on 15.07.15.
 */
public class BaseDrawerActivity extends BaseToolBarActivity {
    private static final String VERSION_TAG = "REQUEST_VERSION_CHECK";
    @Nullable
    @Bind(R.id.drawerLayout)
    DrawerLayout drawerLayout;
    @Nullable
    @Bind(R.id.nv_drawer)
    NavigationView nvDrawer;
    View headerView;
    LinearLayout llLoginMenuHeader;
    TextView tvMenuUserName;
    TextView tvMenuUserLogin;
    ImageView ivAvatar;
    ImageView ivLevel;
    MenuItem miLogout;
    MenuItem miVersion;
    MenuItem miScore;
    MenuItem miLocation;
    MenuItem miAbout;
    MenuItem miSetting;
    MenuItem miTimeuse;
    MenuItem miNetworking;
    Handler mHandler = new Handler();
    BaseService baseService;

    public void login(View view) {
        startActivity(LoginActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBusUtil.getEventBus().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBusUtil.getEventBus().unregister(this);
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
        miVersion = nvDrawer.getMenu().findItem(R.id.menu_version);
        miLocation = nvDrawer.getMenu().findItem(R.id.menu_location);
        miScore = nvDrawer.getMenu().findItem(R.id.menu_score);
        miAbout = nvDrawer.getMenu().findItem(R.id.menu_about);
        miNetworking = nvDrawer.getMenu().findItem(R.id.menu_networking);
        miTimeuse = nvDrawer.getMenu().findItem(R.id.menu_timeuse);
        miSetting = nvDrawer.getMenu().findItem(R.id.menu_setting);
        headerView = nvDrawer.getHeaderView(0);
        llLoginMenuHeader = (LinearLayout) headerView.findViewById(R.id.ll_menu_headview);
        tvMenuUserName = (TextView) headerView.findViewById(R.id.tv_menu_username);
        tvMenuUserLogin = (TextView) headerView.findViewById(R.id.tv_menu_login);
        ivAvatar = (ImageView) headerView.findViewById(R.id.iv_avatar);
        ivLevel = (ImageView) headerView.findViewById(R.id.iv_level);
        nvDrawer.setItemIconTintList(null);
        setUpAbout();
        setUpVersion();
        setUpUserMenu();
        setUpLocation();
        setStateUse(null);
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

    private void setStateUse(StateUseVo vo) {
        if (vo != null) {
            miTimeuse.setTitle(String.format(getString(R.string.menu_btn_timeuse), vo.timeUse));
            miNetworking.setTitle(String.format(getString(R.string.menu_btn_networking), vo.trafficUse));
        } else {
            miTimeuse.setTitle(String.format(getString(R.string.menu_btn_timeuse), "0 h"));
            miNetworking.setTitle(String.format(getString(R.string.menu_btn_networking), "0 Gb"));
        }
    }

    private void setUpAbout() {
        boolean hasClick = getPrefBoolean(this, Constants.ABOUT_FIRST, false);
        if (hasClick) {
            miAbout.setIcon(R.drawable.ic_menu_about);
        } else {
            miAbout.setIcon(R.drawable.ic_menu_about_first);
        }
    }

    private void setUpVersion() {
        checkUpdate(false);
        miVersion.setTitle(String.format(getString(R.string.menu_btn_version), VersionUpdater.getVersion()));
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
            }
            miScore.setTitle(String.format(getString(R.string.menu_btn_score), String.valueOf(vo.score)));
        } else {
            miLogout.setVisible(false);
            llLoginMenuHeader.setEnabled(true);
            tvMenuUserName.setText(R.string.menu_btn_name_default);
            tvMenuUserLogin.setText(R.string.menu_btn_login);
            ivLevel.setVisibility(View.GONE);
            int score = PreferenceUtils.getPrefInt(this, Constants.SCORE_TMP, 0);
            miScore.setTitle(String.format(getString(R.string.menu_btn_score), String.valueOf(score)));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UserLoginEvent event) {
        setUpUserMenu();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(LocationChooseEvent event) {
        setUpLocation();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(StateUseEvent event) {
        setStateUse(event.stateUse);
    }

    public void logout(MenuItem item) {
        baseService.postData(Constants.getUrl(Constants.API_LOGOUT_URL), null, null, null, null, null);
        UserLoginUtil.logout(this);
    }

    @Override
    protected void setupToolbar() {
        super.setupToolbar();
        if (getToolbar() != null) {
            getToolbar().setNavigationIcon(R.drawable.ic_menu_drawer);
            getToolbar().setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    drawerLayout.openDrawer(Gravity.LEFT);
                }
            });
        }
        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {

            }

            @Override
            public void onDrawerOpened(View drawerView) {
                boolean flag = PreferenceUtils.getPrefBoolean(BaseDrawerActivity.this, Constants.LOCATION_FLAG, false);
                int count = PreferenceUtils.getPrefInt(BaseDrawerActivity.this, Constants.LOCATION_FLAG_COUNT, 0);
                if (!flag && count < 3) {
                    Toast.makeText(BaseDrawerActivity.this, R.string.location_choose_flag_none, Toast.LENGTH_LONG).show();
                    count = count + 1;
                    PreferenceUtils.setPrefInt(BaseDrawerActivity.this, Constants.LOCATION_FLAG_COUNT, count);
                }
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
                } else if (item.getItemId() == R.id.menu_version) {
                    name = "版本检测";
                    checkUpdate(true);
                } else if (item.getItemId() == R.id.menu_location) {
                    name = "网络选择";
                    LocationChooseFragment.startFragment(BaseDrawerActivity.this);
                } else if (item.getItemId() == R.id.menu_feedback) {
                    name = "反馈";
                    IWannaFragment.startFragment(BaseDrawerActivity.this);
                } else if (item.getItemId() == R.id.menu_about) {
                    name = "关于";
                    String url = Constants.ABOUT;
                    if (SystemUtils.isZH(BaseDrawerActivity.this)) {
                        url = Constants.ABOUT_ZH;
                    }
                    url = url + "?" + DateUtils.format(new Date(), DateUtils.DATE_FORMAT);
                    WebViewActivity.startWebViewActivity(BaseDrawerActivity.this, url, getString(R.string.menu_btn_about), false, false, null);
                    PreferenceUtils.setPrefBoolean(BaseDrawerActivity.this, Constants.ABOUT_FIRST, true);
                    setUpAbout();
                } else if (item.getItemId() == R.id.menu_score) {
                    name = "积分";
                    Toast.makeText(BaseDrawerActivity.this, R.string.menu_btn_score_context, Toast.LENGTH_SHORT).show();
                } else if (item.getItemId() == R.id.menu_bug) {
                    name = "bug报告";
                    startService(LogUploadService.class);
                    Toast.makeText(BaseDrawerActivity.this, R.string.menu_btn_report_log, Toast.LENGTH_SHORT).show();
                } else if (item.getItemId() == R.id.menu_share) {
                    name = "分享";
                    showShare();
                }else if (item.getItemId() == R.id.menu_setting) {
                    name = "设置";
                    startActivity(SettingActivity.class);
                }
                MobAgent.onEventMenu(BaseDrawerActivity.this, name);
                return false;
            }
        });
    }

    public void showShare() {
        final String url = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.D_URL, null);
        if (!StringUtils.hasText(url)) {
            Toast.makeText(this, R.string.menu_share_copy_error, Toast.LENGTH_SHORT).show();
            return;
        }
        String str = String.format(getResources().getString(R.string.menu_share_url), url);
        AlertDialog.Builder confirmDialog = new AlertDialog.Builder(this);
        confirmDialog.setTitle(R.string.menu_share_title);
        confirmDialog.setMessage(str);
        confirmDialog.setPositiveButton(R.string.menu_share_confirm, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SystemUtils.copy(BaseDrawerActivity.this,url);
                Toast.makeText(BaseDrawerActivity.this, R.string.menu_share_copy_ok, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        confirmDialog.setNegativeButton(R.string.menu_share_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        confirmDialog.show();
    }

    public void checkUpdate(final boolean needToast) {
        // 检查版本更新
        if (VersionUpdater.isInitVersionSuccess()) {
            VersionUpdater.checkNewVersion(BaseDrawerActivity.this, new CommonResponse.ResponseOkListener<VersionVo>() {
                @Override
                public void onResponse(final VersionVo vo) {
                    VersionUpdater.setNewVersion(BaseDrawerActivity.this, vo.maxBuild);
                    PreferenceUtils.setPrefString(MyApplication.getInstance(), Constants.D_URL, vo.url);
                    PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), Constants.ADS_SHOW_CONFIG, vo.adsShow);
                    PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), Constants.LOG_UPLOAD_CONFIG, vo.logUp);
                    PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), Constants.NEED_DNSPOD_CONFIG, vo.needDnspod);
                    if (StringUtils.hasText(vo.dnspodIp)) {
                        HttpDNSUtil.DNS_POD_IP = vo.dnspodIp;
                    }
                    if (vo.stateUse != null)
                        EventBusUtil.getEventBus().post(new StateUseEvent(vo.stateUse));
                    if (VersionUpdater.isNewVersion(vo.maxBuild)
                            && StringUtils.hasText(vo.url)) {
                        // 有新版本
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                miVersion.setIcon(R.drawable.ic_menu_version_new);
                                miVersion.setTitle(R.string.about_version_download_title);
                                VersionUpdater.showUpdateDialog(BaseDrawerActivity.this, vo, true);
                            }
                        }, 300);
                    } else {
                        if(needToast)
                            Toast.makeText(BaseDrawerActivity.this, R.string.about_version_update_to_date, Toast.LENGTH_SHORT).show();
                    }
                }
            }, new CommonResponse.ResponseErrorListener() {

            }, VERSION_TAG);
        }
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
