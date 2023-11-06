package com.openapi.ks.myapp.ui.user;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.openapi.commons.common.util.DateUtils;
import com.openapi.commons.common.util.EventBusUtil;
import com.openapi.commons.common.util.FileSizeUtil;
import com.openapi.commons.common.util.FileUtils;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.common.util.ShareUtil;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.commons.common.util.SystemUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.commons.yewu.net.request.CommonResponse;
import com.openapi.commons.yewu.um.MobAgent;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.form.RegForm;
import com.openapi.ks.myapp.bean.vo.NullReturnVo;
import com.openapi.ks.myapp.bean.vo.StateUseVo;
import com.openapi.ks.myapp.bean.vo.UserInfoVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.BaseService;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.ks.myapp.data.VersionUpdater;
import com.openapi.ks.myapp.data.config.TabChangeEvent;
import com.openapi.ks.myapp.service.LogUploadService;
import com.openapi.ks.myapp.ui.base.WebViewActivity;
import com.openapi.ks.myapp.ui.base.app.BaseSingleActivity;

import java.io.File;
import java.util.Date;

import butterknife.BindView;
import butterknife.OnClick;

import static com.openapi.commons.common.CommonConstants.tmpFilePath;

/**
 * Created by openapi on 2016/8/13.
 */
public class SettingActivity extends BaseSingleActivity {
    private static final String TAG = "setpass_tag";
    @BindView(R.id.sw_area_mi)
    Switch swAreaMi;
    @BindView(R.id.sw_picshow)
    Switch swPicshow;
    @BindView(R.id.sw_area)
    Switch swArea;
    @BindView(R.id.sw_sound)
    Switch swSound;
    @BindView(R.id.sw_playcore)
    Switch swPlayCore;
    @BindView(R.id.sw_playVideo)
    Switch sw_playVideo;
    @BindView(R.id.tv_version)
    TextView tvVersion;
    BaseService baseService;
    String mEmail;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_setting);
        setToolbarTitle(R.string.setting, true);

        sw_playVideo.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.PLAYVIDEO_SWITCH, true));
        sw_playVideo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.PLAYVIDEO_SWITCH, isChecked);
                LogUtil.i("swPlayCore: " + isChecked);
                EventBusUtil.getEventBus().post(new TabChangeEvent());
            }
        });

        swPlayCore.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.PLAYCORE_SWITCH, true));
        swPlayCore.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.PLAYCORE_SWITCH, isChecked);
                LogUtil.i("swPlayCore: " + isChecked);
                EventBusUtil.getEventBus().post(new TabChangeEvent());
            }
        });
        swArea.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.AREA_SWITCH, true));
        swArea.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.AREA_SWITCH, isChecked);
                LogUtil.i("swArea: " + isChecked);
                EventBusUtil.getEventBus().post(new TabChangeEvent());
            }
        });
        swPicshow.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.VIDEO_PIC_SWITCH, true));
        swPicshow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.VIDEO_PIC_SWITCH, isChecked);
                LogUtil.i("VIDEO_PIC_SWITCH: " + isChecked);
            }
        });
        swSound.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.SOUND_SWITCH, true));
        swSound.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.SOUND_SWITCH, isChecked);
                LogUtil.i("SOUND_SWITCH: " + isChecked);
            }
        });
        swAreaMi.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.AREA_MI_SWITCH, false));
        swAreaMi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.AREA_MI_SWITCH   , isChecked);
                LogUtil.i("AREA_MI_SWITCH: " + isChecked);
            }
        });
        baseService = new BaseService();
        baseService.setup(this);
        hidenAds();
        setVersion();
    }

    private void setVersion() {
        tvVersion.setText(VersionUpdater.getVersion());
    }

    @OnClick(R.id.rr_version)
    public void onVersion(View view) {
        VersionUpdater.checkUpdate(this, true);
        MobAgent.onEventMenu(this, "版本");
    }

    @OnClick(R.id.tv_share)
    public void onShare(View view) {
        showShare();
        MobAgent.onEventMenu(this, "分享");
    }

    @Override
    public boolean needShow() {
        return true;
    }

    public void showShare() {
        String url = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.D_URL, null);
        if (!StringUtils.hasText(url)) {
            url = Constants.DEFAULT_REFERER;
        }
        ShareUtil util = new ShareUtil(this);
        util.shareText(null,null,url+" 爱Freedom，精彩你的生活","爱Freedom","精彩你的生活");
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
