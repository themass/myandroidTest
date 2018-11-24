package com.qq.ads.base;

import android.content.Context;
import android.os.Handler;

/**
 * Created by dengt on 2017/9/20.
 */

public abstract class InterstitialAdsInter extends AdsEventAdaptor implements BaseAdsInter{
    public abstract void interstitialAds(Context context, final Handler handler,String key,boolean score);
    public abstract void interstitialExit(Context context,String key);
}
