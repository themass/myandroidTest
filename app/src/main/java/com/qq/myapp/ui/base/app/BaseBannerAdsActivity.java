package com.qq.myapp.ui.base.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import com.qq.common.util.LogUtil;
import com.qq.yewu.ads.reward.AdmobRewardManger;
import com.qq.yewu.ads.base.AdsContext;
import com.qq.yewu.ads.base.AdsManager;
import com.qq.ks.free1.R;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.AdsPopStrategy;
import com.qq.myapp.data.config.HindBannerEvent;
import com.qq.yewu.ads.reward.BaseRewardManger;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.BindView;
import butterknife.OnClick;


/**
 * Created by dengt on 2016/8/21.
 */
public abstract class BaseBannerAdsActivity extends BaseToolBarActivity implements AdmobRewardManger.OnAdmobRewardListener {
    private static final int ANIM_DURATION_FAB = 400;
    @BindView(R.id.fl_content)
    public ViewGroup flContent;
    @BindView(R.id.fl_header)
    public ViewGroup flBanner;
    @BindView(R.id.fab_up)
    public FloatingActionButton fabUp;
    @BindView(R.id.ct_bar)
    public CollapsingToolbarLayout ctBar;
    public BaseRewardManger admobRewardManger;
    private AdsGoneTask task = new AdsGoneTask();
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        }
    };
    @OnClick(R.id.fab_up)
    public void onClickFab(View view) {
        admobRewardManger.showAd();
    }
    @Override
    public void onNoRewardAD(){
        AdsPopStrategy.clickAdsShowBtn(this);
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        admobRewardManger = new BaseRewardManger(this,this);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentViewWithoutInject(R.layout.base_fragment);
        getLayoutInflater().inflate(layoutResID, (ViewGroup) findViewById(R.id.fl_content), true);
        bindViews();
        setupToolbar();
        fabUp.setVisibility(View.GONE);
        if(needGoneBanner())
            mHandler.postDelayed(task, Constants.BANNER_ADS_GONE_LONG);
//        flBanner.setBackgroundResource(R.color.base_white);
    }
    protected boolean needGoneBanner(){
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
        admobRewardManger.onAdResume();
    }

    public void adsDelayGone() {
        mHandler.postDelayed(task, Constants.BANNER_ADS_GONE_SHORT);
    }

    @Override
    protected void onPause() {
        super.onPause();
        hidenAds();
        admobRewardManger.onAdPause();
    }

    public void showAds() {
        if (needShow()) {
            AdsManager.getInstans().showBannerAds(this, flBanner,getBannerCategrey());
        } else {
            flBanner.setVisibility(View.GONE);
        }
    }
    protected AdsContext.AdsFrom getBannerAdsFrom(){
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
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(HindBannerEvent event) {
        hidenAds();
    }
    @Override
    public void onDestroy() {
        AdsManager.getInstans().exitBannerAds(this, flBanner,getBannerCategrey());
        admobRewardManger.onAdDestroy();
        super.onDestroy();
    }
    protected AdsContext.Categrey getBannerCategrey(){
        return AdsContext.Categrey.CATEGREY_VPN1;
    }
    class AdsGoneTask implements Runnable {
        @Override
        public void run() {
            hidenAds();
        }
    }

}
