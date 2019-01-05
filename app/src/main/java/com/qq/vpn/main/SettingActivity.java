package com.qq.vpn.main;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.qq.Constants;
import com.qq.MobAgent;
import com.qq.MyApplication;
import com.qq.ads.base.AdsContext;
import com.qq.ext.util.DateUtils;
import com.qq.ext.util.FileSizeUtil;
import com.qq.ext.util.FileUtils;
import com.qq.ext.util.LogUtil;
import com.qq.ext.util.PreferenceUtils;
import com.qq.ext.util.ShareUtil;
import com.qq.ext.util.StringUtils;
import com.qq.ext.util.SystemUtils;
import com.qq.ext.util.ToastUtil;
import com.qq.fq3.R;
import com.qq.vpn.domain.res.StateUseVo;
import com.qq.vpn.domain.res.UserInfoVo;
import com.qq.vpn.support.NetApiUtil;
import com.qq.vpn.support.UserLoginUtil;
import com.qq.vpn.support.VersionUpdater;
import com.qq.vpn.ui.base.actvity.BaseSingleActivity;

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
    NetApiUtil baseService;
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
        baseService = new NetApiUtil(this);
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
//        MobAgent.onEventMenu(this, "积分");
        onAbout(view);
    }
    @OnClick(R.id.rl_cache)
    public void onCache(View view) {
        ToastUtil.showShort(R.string.menu_btn_cache_context);
        FileUtils.deleteFile(new File(FileUtils.getWriteFilePath(this)));
        MyApplication.getInstance().initFilePath();
        setCache();
        MobAgent.onEventMenu(this, "缓存");
    }
    @OnClick(R.id.rr_version)
    public void onVersion(View view) {
        VersionUpdater.checkUpdate(this, true);
        MobAgent.onEventMenu(this, "版本");
    }
    @OnClick(R.id.tv_auther)
    public void onContract(View view) {
        SystemUtils.copy(SettingActivity.this, "gqli5296@gmail.com");
        ToastUtil.showShort(R.string.menu_copy_emai);
        MobAgent.onEventMenu(this, "联系我们");
    }

    @OnClick(R.id.tv_about)
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

    @OnClick(R.id.tv_share)
    public void onShare(View view) {
        showShare();
        MobAgent.onEventMenu(this, "分享");
    }

    @OnClick(R.id.tv_bug)
    public void onBug(View view) {
        ToastUtil.showShort(R.string.menu_btn_report_log);
        MobAgent.onEventMenu(this, "bug");
    }

    @Override
    public boolean needShow() {
        return false;
    }

    public void showShare() {
//        final String url = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.D_URL, null);
//        if (!StringUtils.hasText(url)) {
//            ToastUtil.showShort(R.string.menu_share_copy_error);
//            return;
//        }
//        String str = String.format(getResources().getString(R.string.menu_share_url), url);
//        AlertDialog.Builder confirmDialog = new AlertDialog.Builder(this);
//        confirmDialog.setTitle(R.string.menu_share_title);
//        confirmDialog.setMessage(str);
//        confirmDialog.setPositiveButton(R.string.menu_share_confirm, new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                SystemUtils.copy(SettingActivity.this, url);
//                ToastUtil.showShort(R.string.menu_share_copy_ok);
//                dialog.dismiss();
//            }
//        });
//        confirmDialog.setNegativeButton(R.string.menu_share_cancel, new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                dialog.dismiss();
//            }
//        });
//        confirmDialog.show();
        String url = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.D_URL, null);
        if (!StringUtils.hasText(url)) {
            url = Constants.DEFAULT_REFERER;
        }
        ShareUtil util = new ShareUtil(this);
        util.shareText(null,null,url+" EuropeVPN，Alive your life", "EuropeVPN","EuropeVPN，Alive your life");
    }

    @Override
    protected boolean enableSliding() {
        return true;
    }
    @Override
    protected AdsContext.Categrey getBannerCategrey(){
        return AdsContext.Categrey.CATEGREY_VPN1;
    }
}
