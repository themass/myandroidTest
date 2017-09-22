package com.sspacee.yewu.ads.base;

import android.content.Context;
import android.os.Handler;

/**
 * Created by themass on 2017/9/20.
 */

public interface BaseAdsInter {
    public void noAds(Context context, Handler handler, AdsContext.AdsFrom from);
    public void readyAds(Context context,Handler handler,AdsContext.AdsFrom from);
    public void clickAds(Context context, Handler handler, AdsContext.AdsFrom from);
    public void displayAds(Context context, Handler handler, AdsContext.AdsFrom from);
    public void closeAds(Context context, Handler handler, AdsContext.AdsFrom from);
}
