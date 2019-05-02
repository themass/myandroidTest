package com.sspacee.yewu.ads.adview;

import android.content.Context;
import android.os.Handler;

import com.kyview.interfaces.AdViewInstlListener;
import com.kyview.manager.AdViewInstlManager;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.InterstitialAdsInter;
/**
 * Created by dengt on 2017/9/20.
 */

public class InterstitialAdviewAds extends InterstitialAdsInter {
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_INTERSTITIAL;
    }
    @Override
    public void interstitialAds(final Context context, final Handler handler,final String key, final boolean score){
        try {
            LogUtil.i("adview interstitialAds req");
            AdViewInstlManager.getInstance(context).requestAd(context, key, new AdViewInstlListener() {

                @Override
                public void onAdClick(String s) {
                    if(!AdsContext.hasClick(context,"interstitial"+s)) {
                        clickAds(context, handler, AdsContext.AdsFrom.ADVIEW);
                    }
                }

                @Override
                public void onAdDisplay(String s) {
                    displayAds(context,handler, AdsContext.AdsFrom.ADVIEW);
                }

                @Override
                public void onAdDismiss(String s) {
                    closeAds(context,handler, AdsContext.AdsFrom.ADVIEW);
                }

                @Override
                public void onAdRecieved(String s) {
                    readyAds(context,handler, AdsContext.AdsFrom.ADVIEW);
                    AdViewInstlManager.getInstance(context)
                            .showAd(context, key);
                }

                @Override
                public void onAdFailed(String s) {
                    noAds(context,handler, AdsContext.AdsFrom.ADVIEW);
                }
            });
        } catch (Throwable e) {
            noAds(context,handler, AdsContext.AdsFrom.ADVIEW);
            LogUtil.e(e);
        }
    }
    @Override
    public void interstitialExit(Context context,String key){

    }
}
