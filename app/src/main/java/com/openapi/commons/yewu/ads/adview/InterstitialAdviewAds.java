package com.openapi.commons.yewu.ads.adview;

import android.content.Context;
import android.os.Handler;

import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.commons.yewu.ads.base.InterstitialAdsInter;

/**
 * Created by openapi on 2017/9/20.
 */

public class InterstitialAdviewAds extends InterstitialAdsInter {
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_INTERSTITIAL;
    }
    @Override
    public void interstitialAds(final Context context, final Handler handler,final String key, final boolean score){
    }
    @Override
    public void interstitialExit(Context context,String key){

    }
}
