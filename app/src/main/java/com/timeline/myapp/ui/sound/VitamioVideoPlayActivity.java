package com.timeline.myapp.ui.sound;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.qq.sexfree.R;
import com.sspacee.common.ui.base.LogActivity;

import com.timeline.myapp.bean.vo.RecommendVo;
import com.timeline.myapp.constant.Constants;

import java.util.HashMap;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import io.vov.vitamio.MediaPlayer;
import io.vov.vitamio.Vitamio;
import io.vov.vitamio.utils.ScreenResolution;
import io.vov.vitamio.utils.StringUtils;
import io.vov.vitamio.widget.VideoView;

/**
 * VideoPlayActivity
 *
 * @author WangZhiYao
 * @date 2016/9/14
 */
public class VitamioVideoPlayActivity extends LogActivity {

    private static final String TAG = "VideoPlayActivity";

    /**
     * 更新播放时间
     */
    private static final int UPDATE_PLAY_TIME = 0x01;
    private static final int UPDATE_TIME = 800;

    /**
     * 隐藏控制条
     */
    private static final int HIDE_CONTROL_BAR = 0x02;

    /**
     * 隐藏控制条时间
     */
    private static final int HIDE_TIME = 3000;

    /**
     * 显示中间控制
     */
    private static final int SHOW_CENTER_CONTROL = 0x03;
    private static final int SHOW_CONTROL_TIME = 1000;

    private final static int ADD_FLAG = 1;
    private final static int SUB_FLAG = -1;

    /**
     * 滑动敏感度
     */
    private static final double SLIP_SENSITIVITY = 0.65;

    /**
     * 最小滑动距离
     */
    private static final int MIN_SLIP_DISTANCE = 200;

    /**
     * 最大滑动距离
     */
    private static final int MAX_SLIP_DISTANCE = 10000;

    /**
     * 设备最大音量
     */
    private static final int MAX_VOLUME = 100;

    /**
     * 设备最小音量
     */
    private static final int MIN_VOLUME = 0;

    /**
     * 设备最大亮度
     */
    private static final int MAX_LIGHTNESS = 255;

    /**
     * 设备最小亮度
     */
    private static final int MIN_LIGHTNESS = 0;

    @BindView(R.id.videoview)
    VideoView mVideoView;
    @BindView(R.id.control_top)
    RelativeLayout mControlTop;
    @BindView(R.id.iv_play)
    ImageView mIvPlay;
    @BindView(R.id.tv_time)
    TextView mTvTime;
    @BindView(R.id.seekbar)
    SeekBar mSeekBar;
    @BindView(R.id.iv_is_fullscreen)
    ImageView mIvIsFullscreen;
    @BindView(R.id.control_bottom)
    RelativeLayout mControlBottom;
    @BindView(R.id.progressbar)
    RelativeLayout mProgressBar;
    @BindView(R.id.iv_control_img)
    ImageView mIvControlImg;
    @BindView(R.id.tv_control)
    TextView mTvControl;
    @BindView(R.id.control_center)
    LinearLayout mControlCenter;
    @BindView(R.id.tv_fast)
    TextView mTvFast;
    @BindView(R.id.video_layout)
    FrameLayout mVideoLayout;
    RecommendVo vo;
    private Unbinder unbinder;
    /**
     * 屏幕宽度
     */
    private int mScreenWidth = 0;

    /**
     * 是否为全屏
     */
    private boolean mIsFullScreen = false;

    /**
     * 视频总时间
     */
    private long mVideoTotalTime = 0;

    /**
     * 是否 快进/快退
     */
    private boolean mIntoSeek = false;

    /**
     * 快进的进度
     */
    private long mSeek = 0;

    /**
     * 是否停止调整进度
     */
    private boolean mIsFastFinish = false;

    private GestureDetector mGestureDetector;
    private AudioManager mAudioManager;

    /**
     * 视频最大音量
     */
    private int mMaxVolume;

    /**
     * 当前音量
     */
    private int mShowVolume;

    /**
     * 当前亮度
     */
    private int mShowLightness;
    HashMap<String,String> header = new HashMap<>();
    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if(mVideoView==null){
                return true;
            }
            switch (msg.what) {
                case UPDATE_PLAY_TIME:
                    long currentPosition = mVideoView.getCurrentPosition();
                    if (currentPosition <= mVideoTotalTime) {
                        // 更新时间显示
                        mTvTime.setText(String.format("%s/%s", sec2time(currentPosition), sec2time(mVideoTotalTime)));
                        // 更新进度条
                        int progress = (int) ((currentPosition * 1.0 / mVideoTotalTime) * 100);
                        mSeekBar.setProgress(progress);
                        mHandler.sendEmptyMessageDelayed(UPDATE_PLAY_TIME, UPDATE_TIME);
                    }
                    break;
                case HIDE_CONTROL_BAR:
                    hideControlBar();
                    break;
                case SHOW_CENTER_CONTROL:
                    mControlCenter.setVisibility(View.GONE);
                    break;
                default:
                    break;
            }
            return false;
        }
    });

    @OnClick({R.id.iv_back, R.id.iv_play, R.id.iv_is_fullscreen})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.iv_back:
                if (mIsFullScreen) {
                    if (mVideoView.isPlaying()) {
                        mHandler.removeMessages(HIDE_CONTROL_BAR);
                        mHandler.sendEmptyMessageDelayed(HIDE_CONTROL_BAR, HIDE_TIME);
                    }
                    setupUnFullScreen();
                } else {
                    finish();
                }
                break;
            case R.id.iv_play:
                if (mVideoView.isPlaying()) {
                    mVideoView.pause();
                    mIvPlay.setImageResource(R.drawable.ic_play_white_36dp);
                    mHandler.removeMessages(UPDATE_PLAY_TIME);
                    mHandler.removeMessages(HIDE_CONTROL_BAR);
                    showControlBar();
                } else {
                    mVideoView.start();
                    mIvPlay.setImageResource(R.drawable.ic_pause_white_36dp);
                    mHandler.sendEmptyMessage(UPDATE_PLAY_TIME);
                    mHandler.sendEmptyMessageDelayed(HIDE_CONTROL_BAR, HIDE_TIME);
                }
                break;
            case R.id.iv_is_fullscreen:
                if (mIsFullScreen) {
                    setupUnFullScreen();
                } else {
                    setupFullScreen();
                }
                if (mVideoView.isPlaying()) {
                    mHandler.removeMessages(HIDE_CONTROL_BAR);
                    mHandler.sendEmptyMessageDelayed(HIDE_CONTROL_BAR, HIDE_TIME);
                }
                break;
            default:
                break;
        }
    }

    /**
     * 秒转化为常见格式
     *
     * @param time 时间
     * @return
     */
    private String sec2time(long time) {
        return StringUtils.generateTime(time);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Vitamio.isInitialized(this);
        setContentView(R.layout.layout_vitamio_video_play);
        unbinder = ButterKnife.bind(this);
        vo = (RecommendVo)getIntent().getSerializableExtra(Constants.CONFIG_PARAM);
        header.put(Constants.REFERER, com.sspacee.common.util.StringUtils.hasText(vo.baseurl)?vo.baseurl : vo.actionUrl);
        header.put(Constants.USER_AGENT, Constants.USER_AGENT_DEF);
        init();
    }

    private void init() {
        // 获取屏幕宽度
        Pair<Integer, Integer> screenPair = ScreenResolution.getResolution(this);
        mScreenWidth = screenPair.first;
        mVideoView.setVideoPath(vo.actionUrl,header);
        // 设置缓冲大小为2M
        mVideoView.setBufferSize(1024 * 1024 * 2);

        initVolumeWithLight();
        addVideoViewListener();
        addSeekBarListener();
        addTouchListener();
    }

    /**
     * 初始化声音和亮度
     */
    private void initVolumeWithLight() {
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mShowVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / mMaxVolume;
        mShowLightness = getScreenBrightness();
    }

    /**
     * 获得当前屏幕亮度值 0--255
     */
    private int getScreenBrightness() {
        int screenBrightness = MAX_LIGHTNESS;
        try {
            screenBrightness = Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return screenBrightness;
    }

    /**
     * 为VideoView添加监听
     */
    private void addVideoViewListener() {
        // 准备播放完成
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){
            public void onPrepared(MediaPlayer mp){
                mVideoTotalTime = mVideoView.getDuration();
            }
        });

        // 正在缓冲
        mVideoView.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener(){
            public void onBufferingUpdate(MediaPlayer mp, int percent){
                if (!mIntoSeek) {
                    mProgressBar.setVisibility(View.VISIBLE);
                }

                mHandler.removeMessages(UPDATE_PLAY_TIME);
                mHandler.removeMessages(HIDE_TIME);
                mIvPlay.setImageResource(R.drawable.ic_play_white_36dp);

                if (mVideoView.isPlaying()) {
                    mVideoView.pause();
                }
            }
        });
        mVideoView.setOnInfoListener(new MediaPlayer.OnInfoListener(){
            public  boolean onInfo(MediaPlayer mp, int what, int extra){
                switch (what) {
                    // 缓冲完成
                    case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                        if (!mVideoView.isPlaying()) {
                            mVideoView.start();
                            mIvPlay.setImageResource(R.drawable.ic_pause_white_36dp);
                            mHandler.removeMessages(UPDATE_PLAY_TIME);
                            mHandler.removeMessages(HIDE_CONTROL_BAR);
                            mHandler.sendEmptyMessage(UPDATE_PLAY_TIME);
                            mHandler.sendEmptyMessageDelayed(HIDE_CONTROL_BAR, HIDE_TIME);
                            mProgressBar.setVisibility(View.GONE);
                        }
                        break;
                    default:
                        break;
                }

                return true;
            }
        });

        // 视频播放出错
        mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener(){
            public boolean onError(MediaPlayer mp, int what, int extra){
                if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
                    Toast.makeText(VitamioVideoPlayActivity.this, "该视频无法播放！", Toast.LENGTH_SHORT).show();
                }

                return true;
            }
        });

        // 视频播放完成
        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
            public void onCompletion(MediaPlayer mp){
                Toast.makeText(VitamioVideoPlayActivity.this, "视频播放完成", Toast.LENGTH_SHORT).show();
                mHandler.removeMessages(UPDATE_PLAY_TIME);
                mHandler.removeMessages(HIDE_CONTROL_BAR);
            }
        });
    }

    /**
     * 为SeekBar添加监听
     */
    private void addSeekBarListener() {
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                long progress = (long) (seekBar.getProgress() * 1.0 / 100 * mVideoView.getDuration());
                mTvTime.setText(String.format("%s/%s", sec2time(progress), sec2time(mVideoTotalTime)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mHandler.removeMessages(UPDATE_PLAY_TIME);
                mHandler.removeMessages(HIDE_CONTROL_BAR);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                long progress = (long) (seekBar.getProgress() * 1.0 / 100 * mVideoView.getDuration());
                mVideoView.seekTo(progress);
                mHandler.sendEmptyMessage(UPDATE_PLAY_TIME);
            }
        });
    }

    /**
     * 添加手势操作
     */
    private void addTouchListener() {
        GestureDetector.SimpleOnGestureListener mSimpleOnGestureListener = new GestureDetector.SimpleOnGestureListener() {
            // 滑动操作
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                    float distanceX, float distanceY) {
                // 非全屏不进行手势操作
                if (!mIsFullScreen) {
                    return false;
                }

                float x1 = e1.getX();
                float y1 = e1.getY();
                float x2 = e2.getX();
                float y2 = e2.getY();
                float absX = Math.abs(x1 - x2);
                float absY = Math.abs(y1 - y2);

                // distanceX < 0 从左到右
                float absDistanceX = Math.abs(distanceX);
                // distanceY < 0 从上到下
                float absDistanceY = Math.abs(distanceY);

                // Y方向的距离比X方向的大，即 上下 滑动
                if (absDistanceX < absDistanceY && !mIntoSeek) {
                    if (distanceY > 0) {
                        // 向上滑动
                        if (x1 >= mScreenWidth * SLIP_SENSITIVITY) {
                            // 右边调节声音
                            changeVolume(ADD_FLAG);
                        } else {
                            // 调节亮度
                            changeLightness(ADD_FLAG);
                        }
                    } else {
                        // 向下滑动
                        if (x1 >= mScreenWidth * SLIP_SENSITIVITY) {
                            changeVolume(SUB_FLAG);
                        } else {
                            changeLightness(SUB_FLAG);
                        }
                    }

                } else {
                    // X方向的距离比Y方向的大，即 左右 滑动
                    if (absX > absY) {
                        mIntoSeek = true;
                        onSeekChange(x1, x2);
                        return true;
                    }
                }

                return false;
            }

            // 双击播放暂停
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (mIsFullScreen) {
                    if (mVideoView.isPlaying()) {
                        mVideoView.pause();
                        mIvPlay.setImageResource(R.drawable.ic_play_white_36dp);
                        mHandler.removeMessages(UPDATE_PLAY_TIME);
                        mHandler.removeMessages(HIDE_CONTROL_BAR);
                        showControlBar();
                    } else {
                        mVideoView.start();
                        mIvPlay.setImageResource(R.drawable.ic_pause_white_36dp);
                        mHandler.sendEmptyMessage(UPDATE_PLAY_TIME);
                        mHandler.sendEmptyMessageDelayed(HIDE_CONTROL_BAR, HIDE_TIME);
                    }
                }
                return true;
            }

            // 单击事件
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (!mVideoView.isPlaying()) {
                    return false;
                }

                if (mControlBottom.getVisibility() == View.VISIBLE) {
                    mHandler.removeMessages(HIDE_CONTROL_BAR);
                    hideControlBar();
                } else {
                    showControlBar();
                    mHandler.sendEmptyMessageDelayed(HIDE_CONTROL_BAR, HIDE_TIME);
                }

                return true;
            }
        };

        mGestureDetector = new GestureDetector(this, mSimpleOnGestureListener);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(event);
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            // 手指抬起
            mTvFast.setVisibility(View.GONE);
            mIntoSeek = false;
            if (mIsFastFinish) {
                mVideoView.seekTo(mSeek);
                mIsFastFinish = false;
            }
        }

        return super.onTouchEvent(event);
    }

    /**
     * 左右滑动距离计算快进/快退时间
     */
    private void onSeekChange(float x1, float x2) {
        long currentPosition = mVideoView.getCurrentPosition();
        long seek;

        if (x1 - x2 > MIN_SLIP_DISTANCE) {
            // 向左滑
            if (currentPosition < MAX_SLIP_DISTANCE) {
                currentPosition = 0;
                seek = 0;
                setFlashText(seek);
                mVideoView.seekTo(currentPosition);
            } else {
                float distance = (x1 - x2);
                mVideoView.seekTo(currentPosition - (long) distance * 10);
                seek = currentPosition - (long) distance * 10;
                setFlashText(seek);
            }
        } else if (x2 - x1 > MIN_SLIP_DISTANCE) {
            // 向右滑动
            if (currentPosition + MAX_SLIP_DISTANCE > mVideoView.getDuration()) {
                currentPosition = mVideoView.getDuration();
                mVideoView.seekTo(currentPosition);
                seek = currentPosition;
                setFlashText(seek);
            } else {
                float distance = x2 - x1;
                mVideoView.seekTo(currentPosition + (long) distance * 10);
                seek = currentPosition + (long) distance * 10;
                setFlashText(seek);
            }
        }
    }

    private void setFlashText(long seek) {
        String showTime = StringUtils.generateTime(seek) +
                "/" + StringUtils.generateTime(mVideoView.getDuration());
        mTvFast.setText(showTime);
        mSeek = seek;
        mIsFastFinish = true;

        if (mTvFast.getVisibility() != View.VISIBLE) {
            mTvFast.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 改变声音
     */
    private void changeVolume(int flag) {
        mShowVolume += flag;
        if (mShowVolume > MAX_VOLUME) {
            mShowVolume = MAX_VOLUME;
        } else if (mShowVolume < MIN_VOLUME) {
            mShowVolume = MIN_VOLUME;
        }
        mIvControlImg.setImageResource(R.drawable.volume_icon);
        mTvControl.setText(mShowVolume + "%");
        int tagVolume = mShowVolume * mMaxVolume / 100;
        // tagVolume:音量绝对值
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, tagVolume, 0);

        mHandler.removeMessages(SHOW_CENTER_CONTROL);
        mControlCenter.setVisibility(View.VISIBLE);
        mHandler.sendEmptyMessageDelayed(SHOW_CENTER_CONTROL, SHOW_CONTROL_TIME);
    }

    /**
     * 改变亮度
     */
    private void changeLightness(int flag) {
        mShowLightness += flag;
        if (mShowLightness > MAX_LIGHTNESS) {
            mShowLightness = MAX_LIGHTNESS;
        } else if (mShowLightness <= MIN_LIGHTNESS) {
            mShowLightness = MIN_LIGHTNESS;
        }
        mIvControlImg.setImageResource(R.drawable.lightness_icon);
        mTvControl.setText(mShowLightness * 100 / 255 + "%");
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = mShowLightness / 255f;
        getWindow().setAttributes(lp);

        mHandler.removeMessages(SHOW_CENTER_CONTROL);
        mControlCenter.setVisibility(View.VISIBLE);
        mHandler.sendEmptyMessageDelayed(SHOW_CENTER_CONTROL, SHOW_CONTROL_TIME);
    }

    /**
     * 隐藏控制条
     */
    private void hideControlBar() {
        mControlBottom.setVisibility(View.GONE);
        mControlTop.setVisibility(View.GONE);
    }

    /**
     * 显示控制条
     */
    private void showControlBar() {
        mControlBottom.setVisibility(View.VISIBLE);
        mControlTop.setVisibility(View.VISIBLE);
    }

    /**
     * 设置为全屏
     */
    private void setupFullScreen() {
        // 设置窗口模式
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        getWindow().setAttributes(attrs);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        // 获取屏幕尺寸
        WindowManager manager = this.getWindowManager();
        DisplayMetrics metrics = new DisplayMetrics();
        manager.getDefaultDisplay().getMetrics(metrics);

        // 设置Video布局尺寸
        mVideoLayout.getLayoutParams().width = metrics.widthPixels;
        mVideoLayout.getLayoutParams().height = metrics.heightPixels;

        // 设置为全屏拉伸
        mVideoView.setVideoLayout(VideoView.VIDEO_LAYOUT_SCALE, 0);
        mIvIsFullscreen.setImageResource(R.drawable.not_fullscreen);

        mIsFullScreen = true;
    }

    /**
     * 设置为非全屏
     */
    private void setupUnFullScreen() {
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setAttributes(attrs);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        float width = getResources().getDisplayMetrics().heightPixels;
        float height = dp2px(300.f);
//        float height= getResources().getDisplayMetrics().widthPixels;
        mVideoLayout.getLayoutParams().width = (int) width;
        mVideoLayout.getLayoutParams().height = (int) height;

        // 设置为全屏
        mVideoView.setVideoLayout(VideoView.VIDEO_LAYOUT_SCALE, 0);
        mIvIsFullscreen.setImageResource(R.drawable.play_fullscreen);

        mIsFullScreen = false;
    }

    @Override
    public void onBackPressed() {
        if (mIsFullScreen) {
            setupUnFullScreen();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * dp转px
     *
     * @param dpValue
     * @return
     */
    private int dp2px(float dpValue) {
        final float scale = getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 如果还在播放，则暂停
        if (mVideoView.isPlaying()) {
            mVideoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放资源
        mVideoView.stopPlayback();
        unbinder.unbind();
    }
}