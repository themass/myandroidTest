package com.timeline.vpn.ui.base;

import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;

import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import butterknife.Bind;

/**
 * Created by gqli on 2016/8/21.
 */
public abstract class BaseBannerAdsActivity extends BaseSingleActivity {
    @Bind(R.id.fl_content)
    public ViewGroup flContent;
    @Bind(R.id.fl_banner)
    public ViewGroup flBanner;
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
        super.setContentViewWithoutInject(R.layout.base_banner_view_collaps);
        getLayoutInflater().inflate(layoutResID,(ViewGroup) findViewById(R.id.fl_content),true);
        bindViews();
        setupToolbar();
    }
    @Override
    public void onResume() {
        super.onResume();
        flBanner.setVisibility(View.VISIBLE);
        showBanner();
    }
    private void showBanner(){
        AdsAdview.bannerAds(this, flBanner, mHandler,Constants.ADS_ADVIEW_KEY_ACTIVITY);
    }
    public void adsDelayGone(){
        mHandler.postDelayed(task, Constants.BANNER_ADS_GONE_SHORT);
    }
    @Override
    protected void onPause() {
        super.onPause();
        flBanner.removeAllViews();
        mHandler.removeCallbacks(task);
    }
    class AdsGoneTask implements Runnable{
        @Override
        public void run() {
            if(flBanner!=null){
                flBanner.removeAllViews();
                flBanner.setVisibility(View.GONE);
            }
        }
    }
}
