package com.timeline.myapp.ui.user;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.sspacee.common.util.DateUtils;
import com.sspacee.common.util.FileSizeUtil;
import com.sspacee.common.util.FileUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PackageUtils;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.common.util.StringUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.net.request.CommonResponse;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.myapp.base.MyApplication;
import com.timeline.myapp.bean.form.RegForm;
import com.timeline.myapp.bean.vo.NullReturnVo;
import com.timeline.myapp.bean.vo.StateUseVo;
import com.timeline.myapp.bean.vo.UserInfoVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.BaseService;
import com.timeline.myapp.data.MyUrlUtil;
import com.timeline.myapp.data.UserLoginUtil;
import com.timeline.myapp.data.VersionUpdater;
import com.timeline.myapp.service.LogUploadService;
import com.timeline.myapp.ui.base.WebViewActivity;
import com.timeline.myapp.ui.base.app.BaseSingleActivity;
import com.timeline.nettypea.R;

import java.io.File;
import java.util.Date;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by themass on 2016/8/13.
 */
public class SettingActivity extends BaseSingleActivity {
    private static final String TAG = "setpass_tag";

    @BindView(R.id.sw_sound)
    Switch swSound;
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
    @BindView(R.id.et_email)
    EditText etEmail;
    @BindView(R.id.btn_submit)
    Button btnSubmit;
    BaseService baseService;
    String mEmail;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_setting);
        setToolbarTitle(R.string.setting, true);
        swSound.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.SOUND_SWITCH, true));
        swSound.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.SOUND_SWITCH, isChecked);
                LogUtil.i("SOUND_SWITCH: " + isChecked);
            }
        });
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
        setUserEmail();
        etEmail.clearFocus();
        etEmail.setEnabled(false);
    }
    private  void setUserEmail(){
        if(UserLoginUtil.getUserCache()!=null){
            etEmail.setText(UserLoginUtil.getUserCache().email);
        }
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
    CommonResponse.ResponseOkListener loginListener = new CommonResponse.ResponseOkListener<NullReturnVo>() {
        @Override
        public void onResponse(NullReturnVo vo) {
            ToastUtil.showShort(R.string.email_ok);
            if(btnSubmit!=null && etEmail!=null) {
                btnSubmit.setText(R.string.modify);
                etEmail.setEnabled(false);
                UserLoginUtil.getUserCache().email = mEmail;
                setUserEmail();
            }
        }
    };
    @OnClick(R.id.btn_submit)
    public void setEmail(Button view) {
        if(UserLoginUtil.getUserCache()==null){
            startActivity(LoginActivity.class);
            return;
        }
        if(btnSubmit.getText().equals(getResources().getString(R.string.modify))){
            btnSubmit.setText(R.string.submit);
            etEmail.setEnabled(true);
            return;
        }else {
            String email = etEmail.getText().toString();
            if (!StringUtils.hasText(email) || !email.contains("@")) {
                ToastUtil.showShort(R.string.empty_name_pwd);
                return;
            }
            RegForm form = new RegForm(null, null, null, null, email,null);
            mEmail = email;
            baseService.postData(Constants.getUrl(Constants.API_SETEMAIL_URL), form, loginListener, new CommonResponse.ResponseErrorListener() {
                @Override
                protected void onError() {
                    super.onError();
                    ToastUtil.showShort(R.string.email_fail);
                }
            }, TAG, NullReturnVo.class);
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
    @OnClick(R.id.tv_officialnet)
    public void onOfficialnet(View view) {
        MyUrlUtil.showOfficeNet(this);
        MobAgent.onEventMenu(this, "官网");
    }
    @OnClick(R.id.tv_auther)
    public void onContract(View view) {
        SystemUtils.copy(SettingActivity.this, "gqli5296@gmail.com");
        ToastUtil.showShort(R.string.menu_copy_emai);
        MobAgent.onEventMenu(this, "联系我们");
    }

    @OnClick(R.id.tv_about)
    public void onAbout(View view) {
        MyUrlUtil.showAbout(this);
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
        return false;
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
