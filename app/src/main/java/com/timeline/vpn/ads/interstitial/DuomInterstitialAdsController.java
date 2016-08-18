package com.timeline.vpn.ads.interstitial;

import android.app.Activity;
import android.os.Handler;

import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import cn.domob.android.ads.AdManager;
import cn.domob.android.ads.InterstitialAd;
import cn.domob.android.ads.InterstitialAdListener;

/**
 * Created by gqli on 2016/3/23.
 */
public class DuomInterstitialAdsController extends InterstitialAdsController {
    InterstitialAd mInterstitialAd;
    public DuomInterstitialAdsController(Activity context, Handler handler){
        super(context,handler);
        mInterstitialAd = new InterstitialAd(mContext, Constants.DM_PUBLISHER_ID,
                Constants.DM_INTERSTITIALPPID);
        mInterstitialAd.setInterstitialAdListener(new InterstitialAdListener() {
            @Override
            public void onInterstitialAdReady() {
                mInterstitialAd.showInterstitialAd(mContext);
                mHandler.sendEmptyMessage(Constants.ADS_READY_MSG);
            }
            @Override
            public void onLandingPageOpen() {
                mHandler.sendEmptyMessage(Constants.ADS_LOADINGPAGE_OPEN_MSG);
            }
            @Override
            public void onLandingPageClose() {
                mHandler.sendEmptyMessage(Constants.ADS_LOADINGPAGE_CLOSE_MSG);
            }

            @Override
            public void onInterstitialAdPresent() {
                mHandler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
            }
            @Override
            public void onInterstitialAdDismiss() {
                mHandler.sendEmptyMessage(Constants.ADS_DISMISS_MSG);
            }
            @Override
            public void onInterstitialAdFailed(AdManager.ErrorCode arg0) {
                mHandler.sendEmptyMessage(Constants.ADS_NO_MSG);
            }
            @Override
            public void onInterstitialAdLeaveApplication() {
                LogUtil.i("onInterstitialAdLeaveApplication");
            }
            @Override
            public void onInterstitialAdClicked(InterstitialAd arg0) {
                mHandler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
            }
        });
    }

    @Override
    public void showAds() {

        if(mInterstitialAd.isInterstitialAdReady()){
            mInterstitialAd.showInterstitialAd(mContext);
        }else {
            mInterstitialAd.loadInterstitialAd();
        }
    }
    @Override
    public boolean close(){
        return true;
    }
}
