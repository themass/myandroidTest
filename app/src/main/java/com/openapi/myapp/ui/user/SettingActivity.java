package com.openapi.myapp.ui.user;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.openapi.common.util.DateUtils;
import com.openapi.common.util.FileSizeUtil;
import com.openapi.common.util.FileUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.PreferenceUtils;
import com.openapi.common.util.ShareUtil;
import com.openapi.common.util.StringUtils;
import com.openapi.common.util.SystemUtils;
import com.openapi.common.util.ToastUtil;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.um.MobAgent;
import com.openapi.myapp.base.MyApplication;
import com.openapi.myapp.bean.vo.StateUseVo;
import com.openapi.myapp.bean.vo.UserInfoVo;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.BaseService;
import com.openapi.myapp.data.UserLoginUtil;
import com.openapi.myapp.data.VersionUpdater;
import com.openapi.myapp.service.LogUploadService;
import com.openapi.myapp.ui.base.app.BaseSingleActivity;
import com.openapi.ks.free1.R;

import java.io.File;
import java.util.Date;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by dengt on 2016/8/13.
 */
public class SettingActivity extends BaseSingleActivity {
    private static final String TAG = "setpass_tag";

    @BindView(R.id.sw_notify)
    Switch swNotify;
    @BindView(R.id.tv_timeuse)
    TextView tvTime;
    @BindView(R.id.tv_networking)
    TextView tvNet;
    @BindView(R.id.tv_score)
    TextView tvScore;
    @BindView(R.id.tv_version)
    TextView tvVersion;
    @BindView(R.id.tv_cache)
    TextView tvCache;
    BaseService baseService;
    String mEmail;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_setting);
        setToolbarTitle(R.string.setting, true);
        swNotify.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.NOTIFY_SWITCH, true));
        swNotify.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.NOTIFY_SWITCH, isChecked);
                LogUtil.i("NOTIFY_SWITCH: " + isChecked);
            }
        });
        baseService = new BaseService();
        baseService.setup(this);
        setStateUse();
        setScore();
        setVersion();
        setCache();
    }
    private void setCache(){
        tvCache.setText(FileSizeUtil.getAutoFileOrFilesSize(FileUtils.getWriteFilePath(this)));
    }
    private void setStateUse() {
        StateUseVo vo = PreferenceUtils.getPrefObj(this, Constants.USER_STATUS, StateUseVo.class);
        if (vo != null) {
            tvTime.setText(vo.timeUse);
            tvNet.setText(vo.trafficUse);
        } else {
            tvTime.setText("0 H");
            tvNet.setText("0 Gb");
        }
    }

    private void setScore() {
        UserInfoVo vo = UserLoginUtil.getUserCache();
        if (vo != null) {
            tvScore.setText(vo.score + "");
        } else {
            int score = PreferenceUtils.getPrefInt(this, Constants.SCORE_TMP, 0);
            tvScore.setText(score + "");
        }
    }
    private void setVersion() {
        tvVersion.setText(VersionUpdater.getVersion());
    }

    @OnClick(R.id.rr_point)
    public void onScore(View view) {
//        ToastUtil.showShort(R.string.menu_btn_score_context);
        onAbout(view);
    }
    @OnClick(R.id.rl_cache)
    public void onCache(View view) {
        ToastUtil.showShort(R.string.menu_btn_cache_context);
        FileUtils.deleteFile(new File(FileUtils.getWriteFilePath(this)));
        MyApplication.getInstance().initFilePath();
        setCache();
    }
    @OnClick(R.id.rr_version)
    public void onVersion(View view) {
        VersionUpdater.checkUpdate(this, true);
    }
    @OnClick(R.id.tv_auther)
    public void onContract(View view) {
        SystemUtils.copy(SettingActivity.this, "justbegin010@gmail.com");
        ToastUtil.showShort(R.string.menu_copy_emai);
    }

    @OnClick(R.id.tv_about)
    public void onAbout(View view) {
        String url = Constants.ABOUT;
        url = url + "?" + DateUtils.format(new Date(), DateUtils.DATE_FORMAT_MM);
//        WebViewActivity.startWebViewActivity(this, url, getString(R.string.menu_btn_about), false, false, null);
        PreferenceUtils.setPrefBoolean(this, Constants.ABOUT_FIRST, true);
        final Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(intent, 0);
    }

    @OnClick(R.id.tv_share)
    public void onShare(View view) {
        showShare();
    }

    @OnClick(R.id.tv_bug)
    public void onBug(View view) {
        startService(LogUploadService.class);
        ToastUtil.showShort(R.string.menu_btn_report_log);
        MobAgent.onEventMenu(this, "bug");
    }

    @Override
    public boolean needShow() {
        return false;
    }

    public void showShare() {
        String url = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.D_URL, null);
        if (!StringUtils.hasText(url)) {
            url = Constants.DEFAULT_REFERER;
        }
        ShareUtil util = new ShareUtil(this);
        util.shareText(null,null,url+" FreeVqn,best tool", "FreeVqn","FreeVqn");
    }

    @Override
    protected boolean enableSliding() {
        return true;
    }
    @Override
    protected AdsContext.Categrey getBannerCategrey(){
        return AdsContext.Categrey.CATEGREY_VPN2;
    }
}
