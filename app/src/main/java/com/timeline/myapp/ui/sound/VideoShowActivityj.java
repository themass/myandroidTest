package com.timeline.myapp.ui.sound;


import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.qq.sexfree.R;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;

import com.timeline.myapp.bean.vo.RecommendVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.ImagePhotoLoad;

import java.util.HashMap;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import cn.jzvd.JZUserAction;
import cn.jzvd.JZUserActionStandard;
import cn.jzvd.JZVideoPlayer;
import cn.jzvd.JZVideoPlayerStandard;

/**
 * Created by themass on 2015/9/1.
 */
public class VideoShowActivityj extends AppCompatActivity {
    private Unbinder unbinder;
    @BindView(R.id.jz_video)
    public JZVideoPlayerStandard jzVideo;
    JZVideoPlayer.JZAutoFullscreenListener mSensorEventListener;
    SensorManager mSensorManager;
    RecommendVo vo;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_video_show);
        unbinder = ButterKnife.bind(this);
        vo = (RecommendVo)getIntent().getSerializableExtra(Constants.CONFIG_PARAM);
        HashMap<String,String> header = new HashMap<>();
        header.put(Constants.REFERER, com.sspacee.common.util.StringUtils.hasText(vo.param)?vo.param: vo.actionUrl);
        jzVideo.setUp(vo.actionUrl, JZVideoPlayerStandard.SCREEN_WINDOW_NORMAL, vo.title,header);
//        jzVideo.headData = header;
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorEventListener = new JZVideoPlayer.JZAutoFullscreenListener();
        ImagePhotoLoad.loadCommonImg(this,vo.img,jzVideo.thumbImageView);
        JZVideoPlayer.setJzUserAction(new MyUserActionStandard());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Sensor accelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(mSensorEventListener, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        //home back
        JZVideoPlayer.goOnPlayOnResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(mSensorEventListener);
        JZVideoPlayer.clearSavedProgress(this, null);
        //home back
        JZVideoPlayer.goOnPlayOnPause();
    }
    @Override
    public void onBackPressed() {
        if (JZVideoPlayer.backPress()) {
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
    class MyUserActionStandard implements JZUserActionStandard {

        @Override
        public void  onEvent(int type, Object url, int screen, Object... objects) {
            switch (type) {
                case JZUserAction.ON_CLICK_PAUSE:
                    AdsContext.showRand(VideoShowActivityj.this);
                    break;
                default:break;
            }
        }
    }
}
