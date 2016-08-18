package com.timeline.vpn.ui.base;

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.LocationVo;
import com.timeline.vpn.bean.vo.UserInfoVo;
import com.timeline.vpn.bean.vo.VersionVo;
import com.timeline.vpn.common.net.request.CommonResponse;
import com.timeline.vpn.common.util.EventBusUtil;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.PreferenceUtils;
import com.timeline.vpn.common.util.StringUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.StaticDataUtil;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.data.VersionUpdater;
import com.timeline.vpn.data.config.LocationChooseEvent;
import com.timeline.vpn.data.config.UserLoginEvent;
import com.timeline.vpn.ui.user.LoginActivity;
import com.timeline.vpn.ui.vpn.LocationChooseaActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.Bind;

/**
 * Created by Miroslaw Stanek on 15.07.15.
 */
public class BaseDrawerActivity extends BaseFragmentActivity {
    @Nullable
    @Bind(R.id.drawerLayout)
    DrawerLayout drawerLayout;

    @Nullable
    @Bind(R.id.nvDrawer)
    NavigationView nvDrawer;

    View headerView;
    LinearLayout llLoginMenuHeader;
    TextView tvMenuUserName;
    TextView tvMenuUserLogin;

    MenuItem miLogout;
    MenuItem miVersion;
    MenuItem miLocation;
    Handler mHandler = new Handler();
    private static final String VERSION_TAG="REQUEST_VERSION_CHECK";
    public void login(View view){
        startActivity(LoginActivity.class);
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBusUtil.getEventBus().register(this);
    }
    @Override
    public void setContentView(int layoutResID) {
        LogUtil.i("setContentView   "+"drawer");
        super.setContentViewWithoutInject(R.layout.menu_drawer);
        ViewGroup viewGroup = (ViewGroup) findViewById(R.id.flContentRoot);
        LayoutInflater.from(this).inflate(layoutResID, viewGroup, true);
        bindViews();
        setupToolbar();
        miLogout = nvDrawer.getMenu().findItem(R.id.menu_louout);
        miVersion = nvDrawer.getMenu().findItem(R.id.menu_version);
        miLocation = nvDrawer.getMenu().findItem(R.id.menu_location);
        headerView = nvDrawer.getHeaderView(0);
        llLoginMenuHeader = (LinearLayout) headerView.findViewById(R.id.ll_menu_headview);
        tvMenuUserName = (TextView) headerView.findViewById(R.id.tv_menu_username);
        tvMenuUserLogin = (TextView) headerView.findViewById(R.id.tv_menu_login);
        checkUpdate();
        miVersion.setTitle(String.format(getString(R.string.menu_btn_version),VersionUpdater.getVersion()));
        nvDrawer.setItemIconTintList(null);
        setUpUserMenu();
        setUpLocation();
    }
    private void setUpLocation(){
        LocationVo vo = PreferenceUtils.getPrefObj(this,Constants.LOCATION_CHOOSE, LocationVo.class);
        String name = vo==null?getString(R.string.location_choose_none):vo.ename;
        miLocation.setTitle(getString(R.string.location_choose_hint)+name);
    }
    private void setUpUserMenu(){
        UserInfoVo vo = StaticDataUtil.get(Constants.LOGIN_USER,UserInfoVo.class);
        if(vo!=null){
            miLogout.setVisible(true);
            llLoginMenuHeader.setEnabled(false);
            tvMenuUserName.setText(vo.name);
            tvMenuUserLogin.setVisibility(View.GONE);

        }else{
            miLogout.setVisible(false);
            llLoginMenuHeader.setEnabled(true);
            tvMenuUserName.setText(R.string.menu_btn_name_default);
            tvMenuUserLogin.setVisibility(View.VISIBLE);
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
    public void logout(MenuItem item){
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
        nvDrawer.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                if(item.getItemId()==R.id.menu_louout){
                    logout(item);
                }else if(item.getItemId()==R.id.menu_version){
                    checkUpdate();
                }else if(item.getItemId()==R.id.menu_location){
                    startActivity(LocationChooseaActivity.class);
                }
                return false;
            }
        });
    }
    public void checkUpdate(){
        // 检查版本更新
        if (VersionUpdater.isInitVersionSuccess()) {
            VersionUpdater.checkNewVersion(BaseDrawerActivity.this, new CommonResponse.ResponseOkListener<VersionVo>(){
                @Override
                public void onResponse(final VersionVo vo) {
                    VersionUpdater.setNewVersion(BaseDrawerActivity.this,vo.maxBuild);
                    if (VersionUpdater.isNewVersion(vo.maxBuild)
                            && StringUtils.hasText(vo.url)) {
                        // 有新版本
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                miVersion.setIcon(R.drawable.ic_menu_version_new);
                                miVersion.setTitle(R.string.about_version_download_title);
                                VersionUpdater.showUpdateDialog(BaseDrawerActivity.this, vo.content, vo.url,
                                        vo.version, vo.maxBuild, true);
                            }
                        }, 300);
                    }
                }},new CommonResponse.ResponseErrorListener(){

            },VERSION_TAG);
        }
    }

}
