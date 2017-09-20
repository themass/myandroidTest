package com.timeline.vpn.ui.base.features;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.yewu.ads.base.AdsController;
import com.sspacee.yewu.ads.base.BaseAdsController;
import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.AdsPopStrategy;
import com.timeline.vpn.data.config.HindBannerEvent;
import com.timeline.vpn.ui.base.app.BaseToolBarActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.BindView;
import butterknife.OnClick;


/**
 * Created by themass on 2016/8/21.
 */
public abstract class BaseBannerAdsActivity extends BaseToolBarActivity implements AdsController {
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
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            mHandler.postDelayed(task, Constants.BANNER_ADS_GONE_LONG);
        }
    };
    @OnClick(R.id.fab_up)
    public void onClickFab(View view) {
        AdsPopStrategy.clickAdsShowBtn(this);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentViewWithoutInject(R.layout.base_fragment);
        getLayoutInflater().inflate(layoutResID, (ViewGroup) findViewById(R.id.fl_content), true);
        bindViews();
        setupToolbar();
        EventBusUtil.getEventBus().register(this);
        fabUp.setVisibility(View.GONE);
//        flBanner.setBackgroundResource(R.color.base_white);
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
            LogUtil.i("显示广告啦");
            BaseAdsController.bannerAds(context, flBanner,getBannerAdsFrom());
        } else {
            flBanner.setVisibility(View.GONE);
        }
    }
    protected BaseAdsController.AdsFrom getBannerAdsFrom(){
        return BaseAdsController.AdsFrom.ADVIEW;
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
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(HindBannerEvent event) {
        hidenAds(this);
    }
    @Override
    public void onDestroy() {
        BaseAdsController.exitBanner(this,flBanner);
        EventBusUtil.getEventBus().unregister(this);
        super.onDestroy();
    }

    class AdsGoneTask implements Runnable {
        @Override
        public void run() {
            hidenAds(BaseBannerAdsActivity.this);
        }
    }

}
