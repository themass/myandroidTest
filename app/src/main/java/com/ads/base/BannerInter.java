package com.ads.base;

import android.content.Context;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.view.ViewGroup;

/**
 * Created by themass on 2017/9/20.
 */

public abstract class BannerInter extends AdsEventAdaptor implements BaseAdsInter{
    public abstract void bannerAds(Context context, ViewGroup group, String key, Handler handler);
    public abstract void bannerExit(FragmentActivity context, ViewGroup group, String key);

}
