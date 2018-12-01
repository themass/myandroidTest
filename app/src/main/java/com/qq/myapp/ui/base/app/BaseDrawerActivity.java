package com.qq.myapp.ui.base.app;

import android.content.Intent;
import android.net.Uri;
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

import com.qq.common.util.DateUtils;
import com.qq.common.util.LogUtil;
import com.qq.common.util.PreferenceUtils;
import com.qq.common.util.ShareUtil;
import com.qq.common.util.StringUtils;
import com.qq.common.util.SystemUtils;
import com.qq.yewu.ads.base.AdsManager;
import com.qq.yewu.um.MobAgent;
import com.qq.myapp.base.MyApplication;
import com.qq.myapp.bean.vo.UserInfoVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.BaseService;
import com.qq.myapp.data.LocationUtil;
import com.qq.myapp.data.UserLoginUtil;
import com.qq.myapp.data.VersionUpdater;
import com.qq.myapp.data.config.LocationChooseEvent;
import com.qq.myapp.data.config.StateUseEvent;
import com.qq.myapp.data.config.UserLoginEvent;
import com.qq.myapp.data.config.VipDescEvent;
import com.qq.myapp.ui.base.WebViewActivity;
import com.qq.myapp.ui.feedback.FeedbackChooseFragment;
import com.qq.myapp.ui.fragment.LocationPageViewFragment;
import com.qq.myapp.ui.user.LoginActivity;
import com.qq.myapp.ui.user.SettingActivity;
import com.qq.fq2.R;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Date;

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
        setupLocationIcon();
    }

    private void setUpVersion() {
        VersionUpdater.checkUpdate(BaseDrawerActivity.this,false);
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
        tvDesc.setText(event.stateUse.desc);
        tvDesc1.setText(event.stateUse.desc1);
        tvDesc2.setText(event.stateUse.desc2);
//        tvDesc.setText("每周减50积分，VIP状态随积分变动");
//        tvDesc1.setText("VIP1=400积分； VIP2=600积分");
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
                    score=score+"(VIP3有效期"+vo.paidTime+")";
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
        url = url + "?" + DateUtils.format(new Date(), DateUtils.DATE_FORMAT_MM);
//        WebViewActivity.startWebViewActivity(this, url, getString(R.string.menu_btn_about), false, false, null);
        PreferenceUtils.setPrefBoolean(this, Constants.ABOUT_FIRST, true);
        MobAgent.onEventMenu(this, "关于");
        final Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(intent, 0);
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
                    FeedbackChooseFragment.startFragment(BaseDrawerActivity.this);
                }  else if (item.getItemId() == R.id.menu_location) {
                    name = "地理";
                    LocationPageViewFragment.startFragment(BaseDrawerActivity.this);
                }else if (item.getItemId() == R.id.menu_setting) {
                    name = "设置";
                    startActivity(SettingActivity.class);
                }
                else if (item.getItemId() == R.id.menu_share) {
                    showShare();
                    name = "分享";
                }
                MobAgent.onEventMenu(BaseDrawerActivity.this, name);
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
        util.shareText(null,null,url+" Free为屁嗯，最好的梯子，没有之一", "Free为屁嗯","Free为屁嗯，最好的梯子，没有之一");
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
