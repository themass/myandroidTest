package com.sspacee.yewu.ads.mobvista;

import android.content.Context;
import android.os.Handler;

import com.mintegral.msdk.MIntegralConstans;
import com.mintegral.msdk.out.InterstitialListener;
import com.mintegral.msdk.out.MTGInterstitialHandler;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.InterstitialAdsInter;
import com.timeline.myapp.constant.Constants;

import java.util.HashMap;

/**
 * Created by dengt on 2017/9/20.
 */

public class InterstitialMobvAds extends InterstitialAdsInter {
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_INTERSTITIAL;
    }
    @Override
    public void interstitialAds(final Context context, final Handler handler,final String key, final boolean score){
        try {
            LogUtil.i("mobv interstitialAds req");
            HashMap<String, Object> hashMap = new HashMap<String, Object>();
            // 设置广告位ID
            hashMap.put(MIntegralConstans.PROPERTIES_UNIT_ID, Constants.Mob_UNIT_INTV);
            final MTGInterstitialHandler mInterstitialHandler = new MTGInterstitialHandler(context, hashMap);
            mInterstitialHandler.setInterstitialListener(new InterstitialListener() {
                @Override
                public void onInterstitialShowSuccess() {
                    displayAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
                }

                @Override
                public void onInterstitialShowFail(String errorMsg) {
                    noAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
                    LogUtil.e("mobv onInterstitialShowFail:"+errorMsg);
                }

                @Override
                public void onInterstitialLoadSuccess() {
                    readyAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
                    mInterstitialHandler.show();
                }

                @Override
                public void onInterstitialLoadFail(String errorMsg) {
                    noAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
                    LogUtil.e("mobv onInterstitialLoadFail:"+errorMsg);
                }

                @Override
                public void onInterstitialClosed() {
                    closeAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
                }

                @Override
                public void onInterstitialAdClick() {
                    clickAds(context, handler, AdsContext.AdsFrom.MOBVISTA);
                }
            });
            mInterstitialHandler.preload();
        } catch (Throwable e) {
            noAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
            LogUtil.e(e);
        }
    }
    @Override
    public void interstitialExit(Context context,String key){

    }
}
