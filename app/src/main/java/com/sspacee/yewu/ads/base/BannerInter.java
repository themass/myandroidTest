package com.sspacee.yewu.ads.base;

import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.view.ViewGroup;

/**
 * Created by themass on 2017/9/20.
 */

public abstract class BannerInter extends AdsEventAdaptor implements BaseAdsInter{
    public abstract void bannerAds(FragmentActivity context, ViewGroup group,String key, Handler handler);
    public abstract void bannerExit(FragmentActivity context,ViewGroup group,String key);

}