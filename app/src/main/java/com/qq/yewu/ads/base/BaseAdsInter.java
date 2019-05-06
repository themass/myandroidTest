package com.qq.yewu.ads.base;

import android.content.Context;
import android.os.Handler;

/**
 * Created by dengt on 2017/9/20.
 */

public interface BaseAdsInter {
    public void noAds(Context context, Handler handler, AdsContext.AdsFrom from, int count);
    public void readyAds(Context context, Handler handler, AdsContext.AdsFrom from);
    public void clickAds(Context context, Handler handler, AdsContext.AdsFrom from);
    public void displayAds(Context context, Handler handler, AdsContext.AdsFrom from);
    public void closeAds(Context context, Handler handler, AdsContext.AdsFrom from);
}
