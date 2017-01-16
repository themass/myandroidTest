package com.timeline.vpn.ui.base;

import android.content.ClipData;
import android.content.ClipboardManager;
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

import com.timeline.vpn.R;
import com.timeline.vpn.base.MyApplication;
import com.timeline.vpn.bean.vo.UserInfoVo;
import com.timeline.vpn.bean.vo.VersionVo;
import com.timeline.vpn.common.net.request.CommonResponse;
import com.timeline.vpn.common.util.EventBusUtil;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.PreferenceUtils;
import com.timeline.vpn.common.util.StringUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.LocationUtil;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.data.VersionUpdater;
import com.timeline.vpn.data.config.LocationChooseEvent;
import com.timeline.vpn.data.config.UserLoginEvent;
import com.timeline.vpn.service.LogUploadService;
import com.timeline.vpn.ui.feedback.ConversationDetailActivity;
import com.timeline.vpn.ui.user.LoginActivity;
import com.timeline.vpn.ui.vpn.LocationChooseFragment;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.Bind;

/**
 * Created by Miroslaw Stanek on 15.07.15.
 */
public class BaseDrawerActivity extends BaseFragmentActivity {
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
    MenuItem miLogout;
    MenuItem miVersion;
    MenuItem miScore;
    MenuItem miLocation;
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
        headerView = nvDrawer.getHeaderView(0);
        llLoginMenuHeader = (LinearLayout) headerView.findViewById(R.id.ll_menu_headview);
        tvMenuUserName = (TextView) headerView.findViewById(R.id.tv_menu_username);
        tvMenuUserLogin = (TextView) headerView.findViewById(R.id.tv_menu_login);
        ivAvatar = (ImageView) headerView.findViewById(R.id.iv_avatar);
        checkUpdate();
        miVersion.setTitle(String.format(getString(R.string.menu_btn_version), VersionUpdater.getVersion()));
        nvDrawer.setItemIconTintList(null);
        setUpUserMenu();
        setUpLocation();
        baseService = new BaseService();
        baseService.setup(this);
    }

    private void setUpLocation() {
        miLocation.setTitle(LocationUtil.getSelectName(this));
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
            miScore.setTitle(String.format(getString(R.string.menu_btn_score), String.valueOf(vo.score)));

        } else {
            miLogout.setVisible(false);
            llLoginMenuHeader.setEnabled(true);
            tvMenuUserName.setText(R.string.menu_btn_name_default);
            tvMenuUserLogin.setText(R.string.menu_btn_login);
            miScore.setTitle(String.format(getString(R.string.menu_btn_score), String.valueOf(0)));
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
        nvDrawer.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                if (item.getItemId() == R.id.menu_louout) {
                    logout(item);
                } else if (item.getItemId() == R.id.menu_version) {
                    checkUpdate();
                } else if (item.getItemId() == R.id.menu_location) {
                    LocationChooseFragment.startFragment(BaseDrawerActivity.this);
                } else if (item.getItemId() == R.id.menu_feedback) {
                    startActivity(ConversationDetailActivity.class);
                } else if (item.getItemId() == R.id.menu_about) {
                    Toast.makeText(BaseDrawerActivity.this, R.string.menu_btn_about_context, Toast.LENGTH_LONG).show();
                } else if (item.getItemId() == R.id.menu_score) {
                    Toast.makeText(BaseDrawerActivity.this, R.string.menu_btn_score_context, Toast.LENGTH_SHORT).show();
                } else if (item.getItemId() == R.id.menu_bug) {
                    startService(LogUploadService.class);
                    Toast.makeText(BaseDrawerActivity.this, R.string.menu_btn_report_log, Toast.LENGTH_SHORT).show();
                } else if (item.getItemId() == R.id.menu_share) {
                    showShare();
                }
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
                ClipboardManager myClipboard;
                myClipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData myClip;
                myClip = ClipData.newPlainText("text", url);
                myClipboard.setPrimaryClip(myClip);
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

    public void checkUpdate() {
        // 检查版本更新
        if (VersionUpdater.isInitVersionSuccess()) {
            VersionUpdater.checkNewVersion(BaseDrawerActivity.this, new CommonResponse.ResponseOkListener<VersionVo>() {
                @Override
                public void onResponse(final VersionVo vo) {
                    VersionUpdater.setNewVersion(BaseDrawerActivity.this, vo.maxBuild);
                    PreferenceUtils.setPrefString(MyApplication.getInstance(), Constants.D_URL, vo.url);
                    PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), Constants.ADS_SHOW_CONFIG, vo.adsShow);
                    PreferenceUtils.setPrefBoolean(MyApplication.getInstance(), Constants.LOG_UPLOAD_CONFIG, vo.logUp);
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
                        Toast.makeText(BaseDrawerActivity.this, R.string.about_version_update_to_date, Toast.LENGTH_SHORT).show();
                    }
                }
            }, new CommonResponse.ResponseErrorListener() {

            }, VERSION_TAG);
        }
    }

}
