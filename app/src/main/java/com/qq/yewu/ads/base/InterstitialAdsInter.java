package com.qq.yewu.ads.base;

import android.content.Context;
import android.os.Handler;

import com.qq.yewu.ads.base.BaseAdsInter;

/**
 * Created by dengt on 2017/9/20.
 */

public abstract class InterstitialAdsInter extends com.qq.yewu.ads.base.AdsEventAdaptor implements BaseAdsInter {
    public abstract void interstitialAds(Context context, final Handler handler,String key,boolean score);
    public abstract void interstitialExit(Context context,String key);
}
