package com.openapi.ks.myapp.ui.sound;


import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.openapi.ks.myapp.data.VideoUtil;
import com.openapi.ks.myapp.ui.sound.media.JZMediaExo;
import com.openapi.ks.myapp.ui.sound.media.JZMediaIjk;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.common.util.StringUtils;

import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.ImagePhotoLoad;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import cn.jzvd.Jzvd;
import cn.jzvd.JzvdStd;

/**
 * Created by openapi on 2015/9/1.
 */
public class VideoShowActivityj extends AppCompatActivity {
    private Unbinder unbinder;
    @BindView(R.id.jz_video)
    public JzvdStd jzVideo;
    Jzvd.JZAutoFullscreenListener mSensorEventListener;
    SensorManager mSensorManager;
    RecommendVo vo;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_video_show);
        unbinder = ButterKnife.bind(this);
        vo = (RecommendVo)getIntent().getSerializableExtra(Constants.CONFIG_PARAM);

        boolean playCore = PreferenceUtils.getPrefBoolean(this, Constants.PLAYCORE_SWITCH, true);
        if(!playCore) {
            jzVideo.setUp(vo.actionUrl, vo.title, JzvdStd.SCREEN_NORMAL, JZMediaExo.class);
        } else {
            jzVideo.setUp(vo.actionUrl, vo.title, JzvdStd.SCREEN_NORMAL, JZMediaIjk.class);
        }
        jzVideo.jzDataSource.headerMap = VideoUtil.getVideoSourceHeader(vo.actionUrl, StringUtils.hasText(vo.baseurl) ? vo.baseurl : vo.actionUrl);
//
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorEventListener = new Jzvd.JZAutoFullscreenListener();
        ImagePhotoLoad.loadCommonImg(this,vo.img,jzVideo.posterImageView);
//        Jzvd.set(new MyUserActionStandard());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Sensor accelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(mSensorEventListener, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        //home back
        Jzvd.goOnPlayOnResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(mSensorEventListener);
//        Jzvd.clearSavedProgress(this, null);
        //home back
        Jzvd.goOnPlayOnPause();
    }
    @Override
    public void onBackPressed() {
        if (Jzvd.backPress()) {
            return;
        }
        super.onBackPressed();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        unbinder.unbind();
    }
    @Override
    protected void onNewIntent(Intent intent) {
        LogUtil.i(getClass().getSimpleName() + "-onNewIntent");
        super.onNewIntent(intent);
    }
    protected boolean enableSliding() {
        return true;
    }
//    class MyUserActionStandard implements JZUserActionStandard {
//
//        @Override
//        public void  onEvent(int type, Object url, int screen, Object... objects) {
//            switch (type) {
//                case JZUserAction.ON_CLICK_PAUSE:
//                    AdsContext.showRand(VideoShowActivityj.this);
//                    break;
//                default:break;
//            }
//        }
//    }
}
