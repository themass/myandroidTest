package com.timeline.vpn.ads.interstitial;

import android.app.Activity;
import android.os.Handler;

import com.baidu.mobads.InterstitialAd;
import com.baidu.mobads.InterstitialAdListener;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

/**
 * Created by gqli on 2016/3/23.
 */
public class BaiduInterstitialAdsController extends InterstitialAdsController {
    private InterstitialAd interAd;
    public BaiduInterstitialAdsController(Activity context, Handler handler){
        super(context, handler);
    }
    @Override
    public void showAds() {
        InterstitialAd.setAppSid(mContext, Constants.BAIDU_APPID);
        interAd = new InterstitialAd(mContext, Constants.BAIDU_INTERTERISTALPOSID);
        interAd.setListener(new InterstitialAdListener() {
            @Override
            public void onAdClick(InterstitialAd arg0) {
                mHandler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
            }
            @Override
            public void onAdDismissed() {
                mHandler.sendEmptyMessage(Constants.ADS_DISMISS_MSG);
            }
            @Override
            public void onAdFailed(String arg0) {
                mHandler.sendEmptyMessage(Constants.ADS_NO_MSG);
            }
            @Override
            public void onAdPresent() {
                mHandler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
            }
            @Override
            public void onAdReady() {
                LogUtil.i("onAdReady");
                interAd.showAd(mContext);
                mHandler.sendEmptyMessage(Constants.ADS_READY_MSG);
            }
        });
       if(interAd.isAdReady()){
           interAd.showAd(mContext);
       }else {
           interAd.loadAd();
       }
    }
    @Override
    public boolean close(){
        //baidu bug  ;cannot destory
        if(interAd!=null){
            interAd.destroy();
        }
        return true;
    }
}
