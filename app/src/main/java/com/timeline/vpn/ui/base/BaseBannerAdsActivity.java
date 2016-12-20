package com.timeline.vpn.ui.base;

import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import butterknife.Bind;

/**
 * Created by themass on 2016/8/21.
 */
public abstract class BaseBannerAdsActivity extends BaseSingleActivity {
    private static final int ANIM_DURATION_FAB = 400;
    @Bind(R.id.fl_content)
    public ViewGroup flContent;
    @Bind(R.id.fl_header)
    public ViewGroup flBanner;
    @Bind(R.id.fab_up)
    public FloatingActionButton fabUp;
    private AdsGoneTask task = new AdsGoneTask();
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
            mHandler.postDelayed(task, Constants.BANNER_ADS_GONE_LONG);
        }
    };

    @Override
    public void setContentView(int layoutResID) {
        super.setContentViewWithoutInject(R.layout.base_fragment);
        getLayoutInflater().inflate(layoutResID, (ViewGroup) findViewById(R.id.fl_content), true);
        bindViews();
        setupToolbar();
        fabUp.setVisibility(View.GONE);
        flBanner.setBackgroundResource(R.color.base_white);
    }

    public void setFabUpVisibility(int v) {
        fabUp.setVisibility(v);
    }

    public void setFabUpClickListener(View.OnClickListener l) {
        fabUp.setOnClickListener(l);
    }

    private void startIntroAnimation() {
        LogUtil.i("fabUp--" + getClass().getSimpleName());
        fabUp.setTranslationY(2 * getResources().getDimensionPixelOffset(R.dimen.btn_fab_size));
        fabUp.animate()
                .translationY(0)
                .setInterpolator(new OvershootInterpolator(1.f))
                .setStartDelay(600)
                .setDuration(ANIM_DURATION_FAB)
                .start();
    }

    @Override
    public void onResume() {
        super.onResume();
        flBanner.setVisibility(View.VISIBLE);
//        showBanner();
        startIntroAnimation();
    }

    private void showBanner() {
        AdsAdview.bannerAds(this, flBanner, mHandler, Constants.ADS_ADVIEW_KEY_ACTIVITY);
    }

    public void adsDelayGone() {
        mHandler.postDelayed(task, Constants.BANNER_ADS_GONE_SHORT);
    }

    @Override
    protected void onPause() {
        super.onPause();
        flBanner.removeAllViews();
        mHandler.removeCallbacks(task);
    }

    class AdsGoneTask implements Runnable {
        @Override
        public void run() {
            if (flBanner != null) {
                flBanner.removeAllViews();
                flBanner.setVisibility(View.GONE);
            }
        }
    }
}
