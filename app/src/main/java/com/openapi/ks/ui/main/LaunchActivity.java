package com.openapi.ks.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.openapi.common.ui.base.LogActivity;
import com.openapi.common.util.EventBusUtil;
import com.openapi.common.util.LogUtil;
import com.openapi.myapp.data.config.SplashAdDissmisEvent;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.AdsManager;
import com.openapi.yewu.ads.config.LaunchAdsNext;
import com.openapi.ks.free1.R;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.task.LoginTask;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * Created by dengt on 2016/3/22.
 */
public class LaunchActivity extends LogActivity {
    RelativeLayout ivAds;
    RelativeLayout skipView;
    TextView tvJishi;
    RippleTextView titleView;
    private int max = Constants.STARTUP_SHOW_TIME_6000+1000;
    private int now = 0;
    boolean perm = true;
    private Runnable mStartMainRunnable = new Runnable() {
        @Override
        public void run() {
            launch();
        }
    };
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            now = now + 1000;
            if (now < max) {
                tvJishi.setText((max - now) / 1000 + " s");
                delay1s();
            } else {
                tvJishi.setText(R.string.skip);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_launch);
        ivAds = findViewById(R.id.rl_spread);
        skipView = findViewById(R.id.skip_view);
        tvJishi = findViewById(R.id.tv_shu);
        titleView = findViewById(R.id.launch_title);
        titleView.setText(getString(R.string.launch_title));
        skipView.setOnClickListener(this::skip);
        LoginTask.start(this);
        mHandler.postDelayed(mStartMainRunnable, max);
        EventBusUtil.getEventBus().register(this);
        showAdview();
        playIntro();
    }

    /**
     * 开屏动效（只播放一次）：标题文字做水波纹，波前扫完后副标题淡入。
     * 系统开启"减少动画"时直接显示最终状态。
     */
    private void playIntro() {
        titleView.play(2000);
        revealText(findViewById(R.id.launch_subtitle), 1500);
        View bottom = findViewById(R.id.launch_bottom);
        if (bottom != null) {
            revealText(bottom, 1800);
        }
        startMarquee();
    }

    /**
     * 右下角 "Look the world"：从右侧滑入，滚到完整显示的位置后停住（不循环）。
     * 系统开启"减少动画"时直接显示。
     */
    private void startMarquee() {
        final View box = findViewById(R.id.launch_marquee_box);
        final TextView text = findViewById(R.id.launch_marquee);
        if (box == null || text == null) {
            return;
        }
        if (Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) {
            return;
        }
        final float offset = 60 * getResources().getDisplayMetrics().density;
        text.setTranslationX(offset);
        text.setAlpha(0f);
        text.animate()
                .translationX(0f)
                .alpha(1f)
                .setStartDelay(1800)
                .setDuration(1600)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void revealText(View v, long delay) {
        if (v == null) {
            return;
        }
        if (Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) {
            v.setAlpha(1f);
            v.setTranslationY(0f);
            return;
        }
        float offset = 10 * getResources().getDisplayMetrics().density;
        v.setAlpha(0f);
        v.setTranslationY(offset);
        v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(900)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    public void skip(View view) {
        if(getResources().getText(R.string.skip).equals(tvJishi.getText()))
            launch();
    }

    private void launch() {
        titleView.cancel();

        Intent intent = new Intent(this, MainFragmentViewPage.class);
        startActivity(intent);
        finish();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    @Override
    protected void onResume() {
        super.onResume();
    }
    private void showAdview(){
//        AdsManager.getInstans().showSplashAds(AdsContext.AdsFrom.ADMOB,this,ivAds,skipView);
        delay1s();
    }
    private void delay1s() {
        mHandler.sendEmptyMessageDelayed(Constants.ADS_JISHI, 1000);
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onBannerShow(LaunchAdsNext next){
        LogUtil.i("LaunchAdsNext "+next.from);
        if(next.from== AdsContext.AdsFrom.MOBVISTA){
//            AdsManager.getInstans().showSplashAds(AdsContext.AdsFrom.ADMOB,this,ivAds,skipView);
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mHandler.removeMessages(Constants.ADS_JISHI);
        mHandler.removeCallbacks(mStartMainRunnable);
//        AdsManager.getInstans().exitSplashAds(this,ivAds);
        super.onDestroy();
        EventBusUtil.getEventBus().unregister(this);
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onADDismissed(SplashAdDissmisEvent event){
        launch();
    }
}
