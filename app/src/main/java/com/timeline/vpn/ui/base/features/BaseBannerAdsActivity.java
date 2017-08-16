package com.timeline.vpn.ui.base.features;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.yewu.ads.adview.AdsAdview;
import com.sspacee.yewu.ads.adview.AdsController;
import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.base.app.BaseToolBarActivity;

import butterknife.Bind;

/**
 * Created by themass on 2016/8/21.
 */
public abstract class BaseBannerAdsActivity extends BaseToolBarActivity implements AdsController {
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

    public void setContentViewWithoutInject(int layoutResID) {
        super.setContentViewWithoutInject(R.layout.base_fragment);
        getLayoutInflater().inflate(layoutResID, (ViewGroup) findViewById(R.id.fl_content), true);
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
        showAds(this);
        startIntroAnimation();
    }

    public void adsDelayGone() {
        mHandler.postDelayed(task, Constants.BANNER_ADS_GONE_SHORT);
    }

    @Override
    protected void onPause() {
        super.onPause();
        hidenAds(this);
    }

    @Override
    public void showAds(Context context) {
        if (needShow(context)) {
            AdsAdview.bannerAds(context, flBanner, mHandler, Constants.ADS_ADVIEW_KEY_ACTIVITY);
        } else {
            flBanner.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean needShow(Context context) {
        return PreferenceUtils.getPrefBoolean(context, Constants.ADS_SHOW_CONFIG, true);
    }

    @Override
    public void hidenAds(Context context) {
        if (flBanner != null) {
            flBanner.removeAllViews();
            flBanner.setVisibility(View.GONE);
        }
        mHandler.removeCallbacks(task);
    }

    class AdsGoneTask implements Runnable {
        @Override
        public void run() {
            hidenAds(BaseBannerAdsActivity.this);
        }
    }

}
