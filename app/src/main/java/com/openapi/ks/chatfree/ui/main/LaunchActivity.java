package com.openapi.ks.chatfree.ui.main;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.icu.text.IDNA;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

// 已移除 FireworkShow 类
// import com.openapi.core.view.view.FireworkShow;
import com.openapi.ks.myapp.ui.base.app.LogActivity;
import com.openapi.core.util.EventBusUtil;
import com.openapi.core.util.LogUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.core.ads.yewu.ads.config.SplashAdDissmisEvent;
import com.openapi.ks.myapp.task.LoginTask;
import com.openapi.core.ads.yewu.ads.base.AdsContext;
import com.openapi.core.ads.yewu.ads.base.AdsManager;
import com.openapi.core.ads.yewu.ads.config.LaunchAdsNext;
import com.openapi.core.ads.yewu.um.MobAgent;


import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;

import com.openapi.ks.chat.databinding.MainLaunchBinding;

/**
 * Created by openapi on 2016/3/22.
 */
public class LaunchActivity extends LogActivity {
    RelativeLayout ivAds;
    RelativeLayout skipView;
    TextView tvJishi;
    RelativeLayout banner1;
    RelativeLayout banner2;
    RelativeLayout banner3;
    LinearLayout llBanner;
//    @BindView(R.id.fireView)
//    FireworkShow fireworkShow;
    private MainLaunchBinding launchBinding;
    private int max = Constants.STARTUP_SHOW_TIME_6000 + 1000;
    private int now = 0;
    boolean perm = true;
    private Runnable mStartMainRunnable = new Runnable() {
        @Override
        public void run() {
            launch();
        }
    };
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
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
        launchBinding = MainLaunchBinding.inflate(getLayoutInflater());
        setContentView(launchBinding.getRoot());
        bindButterViews();
        MobAgent.init(this);
        LoginTask.start(this);
        mHandler.postDelayed(mStartMainRunnable, max);
        EventBusUtil.getEventBus().register(this);
        showAdview();
//        fireworkShow.startFireworkShow();
    }
    public void skip(View view) {
        if (getResources().getText(R.string.skip).equals(tvJishi.getText()))
            launch();
    }

    private void bindButterViews() {
        MainLaunchBinding b = launchBinding;
        ivAds = b.rlSpread;
        skipView = b.skipView;
        tvJishi = b.tvShu;
        banner1 = b.rlBanner1;
        banner2 = b.rlBanner2;
        banner3 = b.rlBanner3;
        llBanner = b.llBanner;
        b.skipView.setOnClickListener(this::skip);
    }

    private void unbindButterViews() {
        ivAds = null;
        skipView = null;
        tvJishi = null;
        banner1 = null;
        banner2 = null;
        banner3 = null;
        llBanner = null;
    }


    private void launch() {
//        fireworkShow.stopFireworkShow();
        Intent intent = new Intent(this, com.openapi.home.HomeActivity.class);
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
        MobAgent.onResume(this);
    }

    private void showAdview() {
        AdsManager.getInstans().showSplashAds(AdsContext.AdsFrom.ADMOB, this, ivAds, skipView);
        delay1s();
    }

    private void delay1s() {
        mHandler.sendEmptyMessageDelayed(Constants.ADS_JISHI, 1000);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onBannerShow(LaunchAdsNext next) {
        LogUtil.i("LaunchAdsNext " + next.from);
//        if (next.from == AdsContext.AdsFrom.ADMOB) {
//            AdsManager.getInstans().showSplashAds(AdsContext.AdsFrom.FACEBOOK, this, ivAds, skipView);
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobAgent.onPause(this);
    }

    @Override
    public void onDestroy() {
        mHandler.removeMessages(Constants.ADS_JISHI);
        mHandler.removeCallbacks(mStartMainRunnable);
        AdsManager.getInstans().exitSplashAds(this, ivAds);
        super.onDestroy();
        EventBusUtil.getEventBus().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onADDismissed(SplashAdDissmisEvent event) {
        AdsManager.getInstans().showSplashAds(AdsContext.AdsFrom.ADVIEW, this, ivAds, skipView);
    }
    private static final int REQUEST_AD_ID_PERMISSION = 1;

}
