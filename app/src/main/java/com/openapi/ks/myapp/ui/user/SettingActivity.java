package com.openapi.ks.myapp.ui.user;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.openai.CommonConstants;
import com.openai.commonutil.EventBusUtil;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.PreferenceUtils;
import com.openai.commonutil.ShareUtil;
import com.openai.commonutil.StringUtils;
import com.openai.commonutil.ToastUtil;
import com.openai.ads.yewu.ads.base.AdsContext;
import com.openai.ads.yewu.um.MobAgent;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.constant.Constants;
import com.openai.net.BaseService;
import com.openapi.ks.myapp.data.VersionUpdater;
import com.openapi.ks.myapp.data.config.PlayCoreEvent;
import com.openapi.ks.myapp.data.config.TabChangeEvent;
import com.openapi.ks.myapp.ui.base.app.BaseSingleActivity;
import com.openapi.ks.myapp.video.PlaybackEngineHelper;
import com.openapi.ks.myapp.ui.fragment.SettingCharacterFragment;

import butterknife.BindView;
import butterknife.OnClick;

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
    @BindView(R.id.sw_music_ui_auxio)
    Switch swMusicUiAuxio;
    @BindView(R.id.sp_playback_engine)
    Spinner spPlaybackEngine;
    @BindView(R.id.sw_playVideo)
    Switch sw_playVideo;

    @BindView(R.id.sw_list_video)
    Switch sw_listVideo;
    @BindView(R.id.sw_tts)
    Switch sw_tts;
    @BindView(R.id.tv_version)
    TextView tvVersion;
    @BindView(R.id.tv_setting_character)
    TextView tvCharacter;
    @BindView(R.id.et_setting_content)
    EditText etCharacter;
    @BindView(R.id.et_volcano_asr_app_key)
    EditText etVolcanoAppKey;
    @BindView(R.id.et_volcano_asr_access_key)
    EditText etVolcanoAccessKey;
    @BindView(R.id.et_volcano_asr_resource_id)
    EditText etVolcanoResourceId;
    @BindView(R.id.et_volcano_asr_cluster)
    EditText etVolcanoCluster;
    @BindView(R.id.sw_volcano_asr_v3)
    Switch swVolcanoAsrV3;
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

        sw_listVideo.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.LISTVIDEO_TINY_SWITCH, true));
        sw_listVideo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.LISTVIDEO_TINY_SWITCH, isChecked);
                LogUtil.i("LISTVIDEO_TINY_SWITCH: " + isChecked);
                EventBusUtil.getEventBus().post(new TabChangeEvent());
            }
        });

        final boolean[] suppressPlaybackSpinner = {true};
        int engine = PlaybackEngineHelper.getEngine(this);
        if (engine >= 0 && engine < spPlaybackEngine.getCount()) {
            spPlaybackEngine.setSelection(engine, false);
        }
        spPlaybackEngine.post(() -> suppressPlaybackSpinner[0] = false);
        spPlaybackEngine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressPlaybackSpinner[0]) {
                    return;
                }
                PlaybackEngineHelper.setEngine(SettingActivity.this, position);
                LogUtil.i("PLAYBACK_ENGINE: " + position);
                ToastUtil.showShort(getString(R.string.playback_engine_toast_fmt,
                        PlaybackEngineHelper.engineLabel(SettingActivity.this, position)));
                EventBusUtil.getEventBus().post(new PlayCoreEvent());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
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
        swMusicUiAuxio.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.MUSIC_UI_AUXIO_STYLE, false));
        swMusicUiAuxio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.MUSIC_UI_AUXIO_STYLE, isChecked);
                LogUtil.i("MUSIC_UI_AUXIO_STYLE: " + isChecked);
                ToastUtil.showShort(isChecked ? R.string.music_ui_auxio_enabled_toast : R.string.music_ui_auxio_disabled_toast);
            }
        });
        swAreaMi.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.AREA_MI_SWITCH, false));
        swAreaMi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.AREA_MI_SWITCH, isChecked);
                LogUtil.i("AREA_MI_SWITCH: " + isChecked);
            }
        });
        sw_tts.setChecked(PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), CommonConstants.TTS_OPEN, false));
        sw_tts.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.setPrefBoolean(SettingActivity.this, CommonConstants.TTS_OPEN, isChecked);
                LogUtil.i("TTS_OPEN: " + isChecked);
            }
        });
        String content = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.MY_SETTING, "");
        if (!StringUtils.isEmpty(content)) {
            etCharacter.setText(content);
        }
        etVolcanoAppKey.setText(PreferenceUtils.getPrefString(this, Constants.VOLCANO_ASR_APP_KEY, ""));
        etVolcanoAccessKey.setText(PreferenceUtils.getPrefString(this, Constants.VOLCANO_ASR_ACCESS_KEY, ""));
        etVolcanoResourceId.setText(PreferenceUtils.getPrefString(this, Constants.VOLCANO_ASR_RESOURCE_ID,
                "volc.bigasr.sauc.duration"));
        etVolcanoCluster.setText(PreferenceUtils.getPrefString(this, Constants.VOLCANO_ASR_CLUSTER,
                "volcengine_input_common"));
        swVolcanoAsrV3.setChecked(PreferenceUtils.getPrefBoolean(this, Constants.VOLCANO_ASR_USE_V3, false));
        swVolcanoAsrV3.setOnCheckedChangeListener((buttonView, isChecked) ->
                PreferenceUtils.setPrefBoolean(SettingActivity.this, Constants.VOLCANO_ASR_USE_V3, isChecked));
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

    @OnClick(R.id.tv_setting_character)
    public void onCharacter(View view) {
        SettingCharacterFragment.startFragment(this);
        MobAgent.onEventMenu(this, "设置人设");
    }

    @OnClick(R.id.bt_setting_save)
    public void onCharacterSave(View view) {
        PreferenceUtils.setPrefString(MyApplication.getInstance(), Constants.MY_SETTING, etCharacter.getText().toString());
        saveVolcanoAsrPrefs();
        MobAgent.onEventMenu(this, "设置人设");

    }

    @Override
    public void onPause() {
        saveVolcanoAsrPrefs();
        super.onPause();
    }

    private void saveVolcanoAsrPrefs() {
        if (etVolcanoAppKey == null) {
            return;
        }
        PreferenceUtils.setPrefString(this, Constants.VOLCANO_ASR_APP_KEY,
                etVolcanoAppKey.getText().toString().trim());
        PreferenceUtils.setPrefString(this, Constants.VOLCANO_ASR_ACCESS_KEY,
                etVolcanoAccessKey.getText().toString().trim());
        PreferenceUtils.setPrefString(this, Constants.VOLCANO_ASR_RESOURCE_ID,
                etVolcanoResourceId.getText().toString().trim());
        PreferenceUtils.setPrefString(this, Constants.VOLCANO_ASR_CLUSTER,
                etVolcanoCluster.getText().toString().trim());
        PreferenceUtils.setPrefBoolean(this, Constants.VOLCANO_ASR_USE_V3, swVolcanoAsrV3.isChecked());
    }

    @Override
    public boolean needShow() {
        return true;
    }

    public void showShare() {
        String url = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.D_URL, null);
        if (!StringUtils.hasText(url)) {
            url = CommonConstants.DEFAULT_REFERER;
        }
        ShareUtil util = new ShareUtil(this);
        util.shareText(null, null, url + " 爱Freedom，精彩你的生活", "爱Freedom", "精彩你的生活");
    }

    @Override
    protected boolean enableSliding() {
        return true;
    }

    @Override
    protected AdsContext.Categrey getBannerCategrey() {
        return AdsContext.Categrey.CATEGREY_VPN2;
    }
}
