package com.openapi.ks.myapp.ui.sound.media;

import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.openapi.commons.common.ui.view.MyFavoriteView;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.ks.moviefree1.R;
import com.openapi.commons.common.util.LogUtil;

import cn.jzvd.JZUtils;
import cn.jzvd.Jzvd;
import cn.jzvd.JzvdStd;

/**
 * Created by Nathen
 * On 2016/04/18 16:15
 */
public class MyJzvdStd extends JzvdStd {

    VelocityTracker mVelocityTracker = VelocityTracker.obtain();
    MyFavoriteView myFavoriteView;
    FrameLayout initLoading;

    public MyJzvdStd(Context context) {
        super(context);
    }

    public MyJzvdStd(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void init(Context context) {
        super.init(context);
        myFavoriteView = findViewById(R.id.my_favoriteview);
        initLoading = findViewById(R.id.loading_init);
    }
    public void loadingInit(){
        initLoading.setVisibility(VISIBLE);
    }
    public void closeLoadingInit(){
        initLoading.setVisibility(GONE);
    }
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int id = v.getId();
        if (id == R.id.surface_container) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    mVelocityTracker.addMovement(event);
                    break;
                case MotionEvent.ACTION_UP:
                    startDismissControlViewTimer();
                    if (mChangePosition) {
                        long duration = getDuration();
                        int progress = (int) (mSeekTimePosition * 100 / (duration == 0 ? 1 : duration));
                        bottomProgressBar.setProgress(progress);
                    }
                    break;
            }
            gestureDetector.onTouchEvent(event);
        } else if (id == R.id.bottom_seek_progress) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    cancelDismissControlViewTimer();
                    break;
                case MotionEvent.ACTION_UP:
                    startDismissControlViewTimer();
                    break;
            }
        }

        return super.onTouch(v, event);
    }

    @Override
    protected void touchActionMove(float x, float y) {

        Log.i(TAG, "onTouch surfaceContainer actionMove [" + this.hashCode() + "] ");
        float deltaX = x - mDownX;
        float deltaY = y - mDownY;
        float absDeltaX = Math.abs(deltaX);
        float absDeltaY = Math.abs(deltaY);
        int maxH = mScreenHeight;
        int maxW = mScreenWidth;
        if (screen == SCREEN_FULLSCREEN) {
             maxH = mScreenHeight;
             maxW = mScreenWidth;
        }else {
            maxH = mScreenWidth;
            maxW = mScreenHeight;
        }
        //拖动的是NavigationBar和状态栏
        if (mDownX > JZUtils.getScreenWidth(getContext()) || mDownY < JZUtils.getStatusBarHeight(getContext())) {
            return;
        }
        if (!mChangePosition && !mChangeVolume && !mChangeBrightness) {
            if (absDeltaX > THRESHOLD || absDeltaY > THRESHOLD) {
                cancelProgressTimer();
                if (absDeltaX >= THRESHOLD) {
                    // 全屏模式下的CURRENT_STATE_ERROR状态下,不响应进度拖动事件.
                    // 否则会因为mediaplayer的状态非法导致App Crash
                    if (state != STATE_ERROR) {
                        mChangePosition = true;
                        mGestureDownPosition = getCurrentPositionWhenPlaying();
                    }
                } else {
                    //如果y轴滑动距离超过设置的处理范围，那么进行滑动事件处理
                    if (mDownX < maxH * 0.5f) {//左侧改变亮度
                        mChangeBrightness = true;
                        WindowManager.LayoutParams lp = JZUtils.getWindow(getContext()).getAttributes();
                        if (lp.screenBrightness < 0) {
                            try {
                                mGestureDownBrightness = Settings.System.getInt(getContext().getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                                Log.i(TAG, "current system brightness: " + mGestureDownBrightness);
                            } catch (Settings.SettingNotFoundException e) {
                                e.printStackTrace();
                            }
                        } else {
                            mGestureDownBrightness = lp.screenBrightness * 255;
                            Log.i(TAG, "current activity brightness: " + mGestureDownBrightness);
                        }
                    } else {//右侧改变声音
                        mChangeVolume = true;
                        if(mAudioManager == null){
                            LogUtil.i("mAudioManager is null");
                            mAudioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
                        }
                        mGestureDownVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    }
                }
            }
        }
        if (mChangePosition) {

            long totalTimeDuration = getDuration();
            if (PROGRESS_DRAG_RATE <= 0) {
                Log.d(TAG, "error PROGRESS_DRAG_RATE value");
                PROGRESS_DRAG_RATE = 1f;
            }
            mVelocityTracker.computeCurrentVelocity(1000);
            float xVelocity = mVelocityTracker.getXVelocity();//速度
            xVelocity = Math.abs(xVelocity);//去绝对值。向左滑，值为负数
            //快速滑动 快进退8s，快速滑动一次，此函数会被调用多次
            if(xVelocity>100){
                if(deltaX>0){
                    mSeekTimePosition = getCurrentPositionWhenPlaying() + 8000;
                }else {
                    mSeekTimePosition = getCurrentPositionWhenPlaying() - 8000;
                }
            }else{
                mSeekTimePosition = (int) (mGestureDownPosition + deltaX * totalTimeDuration / (maxW * PROGRESS_DRAG_RATE));
            }

            LogUtil.i("sudu= "+xVelocity+"---deltaX="+deltaX+"---totalTimeDuration="+totalTimeDuration);

//            mSeekTimePosition = (int) (mGestureDownPosition + deltaX * totalTimeDuration / (maxW * PROGRESS_DRAG_RATE));
            if (mSeekTimePosition > totalTimeDuration)
                mSeekTimePosition = totalTimeDuration;
            String seekTime = JZUtils.stringForTime(mSeekTimePosition);
            String totalTime = JZUtils.stringForTime(totalTimeDuration);

            showProgressDialog(deltaX, seekTime, mSeekTimePosition, totalTime, totalTimeDuration);
        }
        if (mChangeVolume) {
            if(mAudioManager == null){
                LogUtil.i("mAudioManager is null");
                mAudioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            }
            deltaY = -deltaY;
            int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int deltaV = (int) (max * deltaY * 3 / maxH);
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mGestureDownVolume + deltaV, 0);
            //dialog中显示百分比
            int volumePercent = (int) (mGestureDownVolume * 100 / max + deltaY * 3 * 100 / maxH);
            showVolumeDialog(-deltaY, volumePercent);
        }

        if (mChangeBrightness) {
            deltaY = -deltaY;
            int deltaV = (int) (255 * deltaY * 3 / maxH);
            WindowManager.LayoutParams params = JZUtils.getWindow(getContext()).getAttributes();
            if (((mGestureDownBrightness + deltaV) / 255) >= 1) {//这和声音有区别，必须自己过滤一下负值
                params.screenBrightness = 1;
            } else if (((mGestureDownBrightness + deltaV) / 255) <= 0) {
                params.screenBrightness = 0.01f;
            } else {
                params.screenBrightness = (mGestureDownBrightness + deltaV) / 255;
            }
            JZUtils.getWindow(getContext()).setAttributes(params);
            //dialog中显示百分比
            int brightnessPercent = (int) (mGestureDownBrightness * 100 / 255 + deltaY * 3 * 100 / maxH);
            showBrightnessDialog(brightnessPercent);
//                        mDownY = y;
        }
    }
    public boolean setSpeed(float speed){
        if(mediaInterface != null) {
            mediaInterface.setSpeed(speed);
            return true;
        }
        ToastUtil.showShort("请先播放视频");
        return false;
    }
    @Override
    public int getLayoutId() {
        return R.layout.my_jzvd_layout;
    }

}
