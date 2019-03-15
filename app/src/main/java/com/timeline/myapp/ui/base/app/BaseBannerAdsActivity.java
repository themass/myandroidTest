package com.timeline.myapp.ui.base.app;

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

import com.qq.e.ads.nativ.NativeExpressADView;
import com.qq.sexfree.R;
import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdmobRewardManger;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;

import com.sspacee.yewu.ads.base.GdtNativeManager;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.AdsPopStrategy;
import com.timeline.myapp.data.config.HindBannerEvent;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Collections;
import java.util.HashMap;

import butterknife.BindView;
import butterknife.OnClick;


/**
 * Created by themass on 2016/8/21.
 */
public abstract class BaseBannerAdsActivity extends BaseToolBarActivity implements GdtNativeManager.OnLoadListener, AdmobRewardManger.OnAdmobRewardListener {
    private static final int ANIM_DURATION_FAB = 400;
    @BindView(R.id.fl_content)
    public ViewGroup flContent;
    @BindView(R.id.fl_header)
    public ViewGroup flBanner;
    @BindView(R.id.fab_up)
    public FloatingActionButton fabUp;
    @BindView(R.id.ct_bar)
    public CollapsingToolbarLayout ctBar;
    private AdsGoneTask task = new AdsGoneTask();
    public AdmobRewardManger admobRewardManger;

    GdtNativeManager gdtNativeManager = new GdtNativeManager(this,Constants.FIRST_AD_POSITION,Constants.FIRST_AD_POSITION,Constants.ITEMS_PER_AD_BANNER);
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        }
    };
    public void onload(HashMap<Integer, NativeExpressADView> mAdViewPositionMap){
        if(!CollectionUtils.isEmpty(mAdViewPositionMap)){
            gdtNativeManager.showAds(Constants.FIRST_AD_POSITION,flBanner);

        }else{
            AdsManager.getInstans().showBannerAds(this, flBanner,getBannerCategrey());
        }
    }
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
        admobRewardManger = new AdmobRewardManger(this,this);
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
        return false;
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
        admobRewardManger.onAdPause();
        if(gdtNativeManager.getAdSize()==0) {
            LogUtil.i("hidenAds： "+gdtNativeManager.getAdSize());
            hidenAds();
        }
    }
    public void showAds() {
        if (needShow()) {
            if(gdtNativeManager.getAdSize()==0)
                gdtNativeManager.loadDataBanner(this);
        } else {
            flBanner.setVisibility(View.GONE);
        }
    }
    protected AdsContext.AdsFrom getBannerAdsFrom(){
        return AdsContext.AdsFrom.ADVIEW;
    }

    public boolean needShow() {
        return false;
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
