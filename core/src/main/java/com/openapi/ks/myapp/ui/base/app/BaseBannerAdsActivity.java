package com.openapi.ks.myapp.ui.base.app;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import com.openapi.ks.myapp.constant.Routes;
import com.openapi.common.util.log.LogUtil;
import com.openapi.core.ads.yewu.ads.base.AdsContext;
import com.openapi.core.ads.yewu.ads.base.AdsManager;
import com.openapi.core.R;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.core.ads.yewu.ads.AdsPopStrategy;


/**
 * Created by openapi on 2016/8/21.
 */
public abstract class BaseBannerAdsActivity extends BaseToolBarActivity {
    private static final int ANIM_DURATION_FAB = 400;
    public ViewGroup flContent;
    public ViewGroup flBanner;
    public FloatingActionButton fabUp;
    public CollapsingToolbarLayout ctBar;
    private AdsGoneTask task = new AdsGoneTask();

    protected Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
        }
    };

    @Override
    protected void bindBaseViews() {
        super.bindBaseViews();
        flContent = findViewById(R.id.fl_content);
        flBanner = findViewById(R.id.fl_header);
        fabUp = findViewById(R.id.fab_up);
        ctBar = findViewById(R.id.ct_bar);
        fabUp.setOnClickListener(this::onClickFab);
    }

    @Override
    protected ViewGroup contentContainer() {
        return flContent;
    }

    public void onClickFab(View view) {

        startActivity(Routes.setting(this));
//        AdsPopStrategy.clickAdsShowBtn(this);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentViewWithoutInject(R.layout.base_fragment);
        getLayoutInflater().inflate(layoutResID, (ViewGroup) findViewById(R.id.fl_content), true);
        bindViews();
        setupToolbar();
        fabUp.setVisibility(View.GONE);
        if (needGoneBanner())
            mHandler.postDelayed(task, Constants.BANNER_ADS_GONE_LONG);
//        flBanner.setBackgroundResource(R.color.base_white);
    }

    protected boolean needGoneBanner() {
        return true;
    }

    public void disableScrollBanner() {
        AppBarLayout.LayoutParams params =
                (AppBarLayout.LayoutParams) ctBar.getLayoutParams();
        params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS);
        ctBar.setLayoutParams(params);

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
        showAds();
        startIntroAnimation();
    }

    public void adsDelayGone() {
        mHandler.postDelayed(task, Constants.BANNER_ADS_GONE_SHORT);
    }

    @Override
    protected void onPause() {
        super.onPause();
        hidenAds();
    }

    public void showAds() {
        if (needShow()) {
            AdsManager.getInstans().showBannerAds(this, flBanner, getBannerCategrey());
        } else {
            flBanner.setVisibility(View.GONE);
        }
    }

    protected AdsContext.AdsFrom getBannerAdsFrom() {
        return AdsContext.AdsFrom.ADVIEW;
    }

    public boolean needShow() {
        return true;
    }

    public void hidenAds() {
        if (flBanner != null) {
            flBanner.removeAllViews();
            flBanner.setVisibility(View.GONE);
        }
        mHandler.removeCallbacks(task);
    }

    @Override
    public void onDestroy() {
        AdsManager.getInstans().exitBannerAds(this, flBanner, getBannerCategrey());
        super.onDestroy();
    }

    protected AdsContext.Categrey getBannerCategrey() {
        return AdsContext.Categrey.CATEGREY_VPN1;
    }

    class AdsGoneTask implements Runnable {
        @Override
        public void run() {
            hidenAds();
        }
    }

}
