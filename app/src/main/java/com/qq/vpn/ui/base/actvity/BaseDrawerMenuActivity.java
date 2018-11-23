package com.qq.vpn.ui.base.actvity;

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

import com.qq.Constants;
import com.qq.MobAgent;
import com.qq.MyApplication;
import com.qq.ext.util.DateUtils;
import com.qq.ext.util.LogUtil;
import com.qq.ext.util.PreferenceUtils;
import com.qq.ext.util.ShareUtil;
import com.qq.ext.util.StringUtils;
import com.qq.ext.util.SystemUtils;
import com.qq.ext.util.ToastUtil;
import com.qq.network.R;
import com.qq.vpn.domain.res.UserInfoVo;
import com.qq.vpn.main.feedback.FeedbackChooseFragment;
import com.qq.vpn.main.login.SinginActivity;
import com.qq.vpn.main.ui.WebViewActivity;
import com.qq.vpn.support.LocationUtil;
import com.qq.vpn.support.NetApiUtil;
import com.qq.vpn.support.UserLoginUtil;
import com.qq.vpn.support.VersionUpdater;
import com.qq.vpn.support.config.LocationChooseEvent;
import com.qq.vpn.support.config.StateUseEvent;
import com.qq.vpn.support.config.UserLoginEvent;
import com.qq.vpn.support.config.VipDescEvent;
import com.qq.vpn.ui.fragment.LocationPageViewFragment;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Date;

import butterknife.BindView;

/**
 * Created by dengt on 14-03-12.
 */
public class BaseDrawerMenuActivity extends ToolBarActivity {
    protected NetApiUtil api;
    @Nullable
    @BindView(R.id.drawerLayout)
    public DrawerLayout drawerLayout;
    @Nullable
    @BindView(R.id.nv_content)
    NavigationView nvDrawer;
    View headerView;
    LinearLayout llLoginMenuHeader;
    LinearLayout llDesc;
    TextView tvMenuUserName;
    TextView tvMenuUserLogin;
    TextView tvScore;
    TextView tvDesc;
    TextView tvDesc1;
    TextView tvDesc2;
    ImageView ivAvatar;
    ImageView ivLevel;
    MenuItem miLogout;
    MenuItem miLocation;
    MenuItem miSetting;
    public void login(View view) {
        startActivity(SinginActivity.class);
    }
    public void setContentView(int layoutResID) {
        LogUtil.i("setContentView   " + "drawer");
        super.setContentViewWithoutInject(R.layout.common_menu_drawer);
        ViewGroup viewGroup = (ViewGroup) findViewById(R.id.fl_content_root);
        LayoutInflater.from(this).inflate(layoutResID, viewGroup, true);
        bindViews();
        setupToolbar();
        api = new NetApiUtil(this);
        miLogout = nvDrawer.getMenu().findItem(R.id.menu_louout);
        miLocation = nvDrawer.getMenu().findItem(R.id.menu_location);
        miSetting = nvDrawer.getMenu().findItem(R.id.menu_setting);
        headerView = nvDrawer.getHeaderView(0);
        llLoginMenuHeader = (LinearLayout) headerView.findViewById(R.id.ll_menu_headview);
        tvMenuUserName = (TextView) headerView.findViewById(R.id.tv_menu_username);
        tvMenuUserLogin = (TextView) headerView.findViewById(R.id.tv_menu_login);
        tvScore= (TextView) headerView.findViewById(R.id.tv_score);
        tvDesc = (TextView) headerView.findViewById(R.id.tv_desc);
        tvDesc1 = (TextView) headerView.findViewById(R.id.tv_desc1);
        tvDesc2 = (TextView) headerView.findViewById(R.id.tv_desc2);
        ivAvatar = (ImageView) headerView.findViewById(R.id.iv_avatar);
        ivLevel = (ImageView) headerView.findViewById(R.id.iv_level);
        nvDrawer.setItemIconTintList(null);
        setUpVersion();
        setUpUserMenu();
        setUpLocation();
    }

    private void setUpLocation() {
        boolean flag = PreferenceUtils.getPrefBoolean(this, Constants.LOCATION_FLAG, false);
        if (!flag) {
            miLocation.setIcon(R.drawable.ic_menu_location_flag);
        } else {
            miLocation.setIcon(R.drawable.ic_menu_location);
        }
        miLocation.setTitle(LocationUtil.getSelectName(this));
        setupLocationIcon();
    }

    private void setUpVersion() {
        VersionUpdater.checkUpdate(BaseDrawerMenuActivity.this,false);

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
            }else if (Constants.UserLevel.LEVEL_VIP3 == vo.level) {
                ivLevel.setImageResource(R.drawable.ic_level_vip3);
            }else if (Constants.UserLevel.LEVEL_VIP4 == vo.level) {
                ivLevel.setImageResource(R.drawable.ic_level_vip4);
            }
        } else {
            miLogout.setVisible(false);
            llLoginMenuHeader.setEnabled(true);
            tvMenuUserName.setText(R.string.menu_btn_name_default);
            tvMenuUserLogin.setText(R.string.menu_btn_login);
            ivLevel.setVisibility(View.GONE);
        }
        setScore(null);
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
        PreferenceUtils.setPrefObj(this, Constants.USER_STATUS, event.stateUse);
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(VipDescEvent event) {
        if(!StringUtils.hasText(event.stateUse.desc))
        {
            tvDesc.setVisibility(View.GONE);
        }else {
            tvDesc.setVisibility(View.VISIBLE);
            tvDesc.setText(event.stateUse.desc);
        }
        if(!StringUtils.hasText(event.stateUse.desc1))
        {
            tvDesc1.setVisibility(View.GONE);
        }else {
            tvDesc1.setVisibility(View.VISIBLE);
            tvDesc1.setText(event.stateUse.desc1);
        }
        if(!StringUtils.hasText(event.stateUse.desc2))
        {
            tvDesc2.setVisibility(View.GONE);
        }else {
            tvDesc2.setVisibility(View.VISIBLE);
            tvDesc2.setText(event.stateUse.desc2);
        }
        setScore(event.stateUse.score);
    }
    private void setScore(Long inScore) {
        if(inScore!=null){
            tvScore.setText(inScore + "积分");
        }else {
            UserInfoVo vo = UserLoginUtil.getUserCache();
            if (vo != null) {
                String score = vo.score + "积分";
                if(vo.paidTime!=null){
                    score=score+"(有效期"+vo.paidTime+")";
                }
                tvScore.setText(score);
            } else {
                int score = PreferenceUtils.getPrefInt(this, Constants.SCORE_TMP, 0);
                tvScore.setText(score + "积分");
            }
        }
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
        api.postData(Constants.getUrl(Constants.API_LOGOUT_URL), null, null, null, null, null);
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
        if (toolbar != null) {
            toolbar.setNavigationIcon(R.drawable.ic_menu_drawer);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (!closeDrawer()) {
                            drawerLayout.openDrawer(Gravity.LEFT);
                        }
                    }catch(Exception e){
                        LogUtil.e(e);
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
                }  else if (item.getItemId() == R.id.menu_feedback) {
                    name = "反馈";
                    FeedbackChooseFragment.startFragment(BaseDrawerMenuActivity.this);
                }  else if (item.getItemId() == R.id.menu_location) {
                    name = "地理";
                    LocationPageViewFragment.startFragment(BaseDrawerMenuActivity.this);
                }else if (item.getItemId() == R.id.menu_setting) {
                    name = "设置";
//                    startActivity(SettingActivity.class);
                }
                else if (item.getItemId() == R.id.menu_share) {
                    showShare();
                    name = "分享";
                }
                MobAgent.onEventMenu(BaseDrawerMenuActivity.this, name);
                return false;
            }
        });
    }
    public void showShare() {
        String url = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.D_URL, null);
        if (!StringUtils.hasText(url)) {
            url = Constants.DEFAULT_REFERER;
        }
        ShareUtil util = new ShareUtil(this);
        util.shareText(null,null,url+" 灯塔V梯子，精彩你的生活", "灯塔V梯子","精彩你的生活");
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
