package com.timeline.sex.ui.user;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.sspacee.common.util.DateUtils;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.FileSizeUtil;
import com.sspacee.common.util.FileUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.common.util.StringUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.sex.R;
import com.timeline.sex.base.MyApplication;
import com.timeline.sex.bean.vo.StateUseVo;
import com.timeline.sex.bean.vo.UserInfoVo;
import com.timeline.sex.constant.Constants;
import com.timeline.sex.data.UserLoginUtil;
import com.timeline.sex.data.VersionUpdater;
import com.timeline.sex.data.config.TabChangeEvent;
import com.timeline.sex.service.LogUploadService;
import com.timeline.sex.ui.base.WebViewActivity;
import com.timeline.sex.ui.base.app.BaseSingleActivity;

import java.io.File;
import java.util.Date;

import butterknife.BindView;
import butterknife.OnClick;

import static com.sspacee.common.CommonConstants.tmpFilePath;

/**
 * Created by themass on 2016/8/13.
 */
public class SettingActivity extends BaseSingleActivity {
    @BindView(R.id.sw_area)
    Switch swArea;
    @BindView(R.id.sw_sound)
    Switch swSound;
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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_setting);
        setToolbarTitle(R.string.setting, true);
        swArea.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.AREA_SWITCH, true));
        swArea.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.AREA_SWITCH, isChecked);
                LogUtil.i("swArea: " + isChecked);
                EventBusUtil.getEventBus().post(new TabChangeEvent());
            }
        });
        swSound.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.SOUND_SWITCH, true));
        swSound.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.SOUND_SWITCH, isChecked);
                LogUtil.i("SOUND_SWITCH: " + isChecked);
            }
        });
        hidenAds();
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
        ToastUtil.showShort(R.string.menu_btn_score_context);
        MobAgent.onEventMenu(this, "积分");
    }
    @OnClick(R.id.rl_cache)
    public void onCache(View view) {
        ToastUtil.showShort(R.string.menu_btn_cache_context);
        FileUtils.deleteFile(new File(FileUtils.getWriteFilePath(this)));
        FileUtils.ensureFile(this, tmpFilePath);
        setCache();
        MobAgent.onEventMenu(this, "缓存");
    }
    @OnClick(R.id.rr_version)
    public void onVersion(View view) {
        VersionUpdater.checkUpdate(this, true);
        MobAgent.onEventMenu(this, "版本");
    }

    @OnClick(R.id.tv_about)
    public void onAbout(View view) {
        String url = Constants.ABOUT;
        if (SystemUtils.isZH(this)) {
            url = Constants.ABOUT_ZH;
        }
        url = url + "?" + DateUtils.format(new Date(), DateUtils.DATE_FORMAT);
        WebViewActivity.startWebViewActivity(this, url, getString(R.string.menu_btn_about), false, false, null);
        PreferenceUtils.setPrefBoolean(this, Constants.ABOUT_FIRST, true);
        MobAgent.onEventMenu(this, "关于");
    }

    @OnClick(R.id.tv_share)
    public void onShare(View view) {
        showShare();
        MobAgent.onEventMenu(this, "分享");
    }

    @OnClick(R.id.tv_bug)
    public void onBug(View view) {
        startService(LogUploadService.class);
        ToastUtil.showShort(R.string.menu_btn_report_log);
        MobAgent.onEventMenu(this, "bug");
    }

    @Override
    public boolean needShow() {
        return true;
    }

    public void showShare() {
        final String url = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.D_URL, null);
        if (!StringUtils.hasText(url)) {
            ToastUtil.showShort(R.string.menu_share_copy_error);
            return;
        }
        String str = String.format(getResources().getString(R.string.menu_share_url), url);
        AlertDialog.Builder confirmDialog = new AlertDialog.Builder(this);
        confirmDialog.setTitle(R.string.menu_share_title);
        confirmDialog.setMessage(str);
        confirmDialog.setPositiveButton(R.string.menu_share_confirm, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SystemUtils.copy(SettingActivity.this, url);
                ToastUtil.showShort(R.string.menu_share_copy_ok);
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

    @Override
    protected boolean enableSliding() {
        return true;
    }
    @Override
    protected AdsContext.Categrey getBannerCategrey(){
        return AdsContext.Categrey.CATEGREY_VPN2;
    }
}
