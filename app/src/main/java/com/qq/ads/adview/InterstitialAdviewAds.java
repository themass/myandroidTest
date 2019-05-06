package com.qq.ads.adview;

import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import com.kyview.interfaces.AdViewInstlListener;
import com.kyview.manager.AdViewInstlManager;
import com.qq.Constants;
import com.qq.ads.base.AdsContext;
import com.qq.ads.base.InterstitialAdsInter;
import com.qq.ext.util.LogUtil;
import com.qq.network.R;
import com.qq.vpn.support.task.ScoreTask;

/**
 * Created by dengt on 2017/9/20.
 */

public class InterstitialAdviewAds extends InterstitialAdsInter {
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_INTERSTITIAL;
    }
    @Override
    public void interstitialAds(final Context context, final Handler handler,final String key, final boolean score,final int count){
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
                    noAds(context,handler, AdsContext.AdsFrom.ADVIEW,count);
                }
            });
        } catch (Throwable e) {
            noAds(context,handler, AdsContext.AdsFrom.ADVIEW,count);
            LogUtil.e(e);
        }
    }
    @Override
    public void interstitialExit(Context context,String key){

    }
}
