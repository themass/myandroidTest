package com.qq.myapp.ui.user;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.qq.kb.R;
import com.sspacee.common.util.DateUtils;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.FileSizeUtil;
import com.sspacee.common.util.FileUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.common.util.ShareUtil;
import com.sspacee.common.util.StringUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.net.request.CommonResponse;
import com.sspacee.yewu.um.MobAgent;

import com.qq.myapp.base.MyApplication;
import com.qq.myapp.bean.form.RegForm;
import com.qq.myapp.bean.vo.NullReturnVo;
import com.qq.myapp.bean.vo.StateUseVo;
import com.qq.myapp.bean.vo.UserInfoVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.BaseService;
import com.qq.myapp.data.UserLoginUtil;
import com.qq.myapp.data.VersionUpdater;
import com.qq.myapp.data.config.TabChangeEvent;
import com.qq.myapp.service.LogUploadService;
import com.qq.myapp.ui.base.WebViewActivity;
import com.qq.myapp.ui.base.app.BaseSingleActivity;

import java.io.File;
import java.util.Date;

import butterknife.BindView;
import butterknife.OnClick;

import static com.sspacee.common.CommonConstants.tmpFilePath;

/**
 * Created by themass on 2016/8/13.
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

    private void setVersion() {
        tvVersion.setText(VersionUpdater.getVersion());
    }

    @OnClick(R.id.rr_point)
    public void onScore(View view) {
//        ToastUtil.showShort(R.string.menu_btn_score_context);
        onAbout(view);
        MobAgent.onEventMenu(this, "积分");
    }
    CommonResponse.ResponseOkListener loginListener = new CommonResponse.ResponseOkListener<NullReturnVo>() {
        @Override
        public void onResponse(NullReturnVo vo) {
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
    @OnClick(R.id.rl_cache)
    public void onCache(View view) {
        ToastUtil.showShort(R.string.menu_btn_cache_context);
        FileUtils.deleteFile(new File(FileUtils.getWriteFilePath(this)));
        FileUtils.ensureFile(this, tmpFilePath);
        setCache();
        MobAgent.onEventMenu(this, "缓存");
    }
    @OnClick(R.id.tv_auther)
    public void onContract(View view) {
        SystemUtils.copy(SettingActivity.this, "gqli5296@gmail.com");
        ToastUtil.showShort(R.string.menu_copy_emai);
        MobAgent.onEventMenu(this, "联系我们");
    }
    @OnClick(R.id.rr_version)
    public void onVersion(View view) {
        VersionUpdater.checkUpdate(this, true);
        MobAgent.onEventMenu(this, "版本");
    }

    @OnClick(R.id.tv_about)
    public void onAbout(View view) {
//        String url = Constants.ABOUT;
//        if (SystemUtils.isZH(this)) {
//            url = Constants.ABOUT_ZH;
//        }
//        url = url + "?" + DateUtils.format(new Date(), DateUtils.DATE_FORMAT_MM);
//        WebViewActivity.startWebViewActivity(this, url, getString(R.string.menu_btn_about), false, false, null);
//        PreferenceUtils.setPrefBoolean(this, Constants.ABOUT_FIRST, true);
//        MobAgent.onEventMenu(this, "关于");
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
        String url = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.D_URL, null);
        if (!StringUtils.hasText(url)) {
            url = Constants.DEFAULT_REFERER;
        }
        ShareUtil util = new ShareUtil(this);
        util.shareText(null,null,url+" 红颜影视，精彩你的生活","红颜影视","精彩你的生活");
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
