package com.qq.yewu.ads.base;

import android.os.Handler;
import androidx.fragment.app.FragmentActivity;
import android.view.ViewGroup;

/**
 * Created by dengt on 2017/9/20.
 */

public abstract class BannerInter extends AdsEventAdaptor implements BaseAdsInter{
    public abstract void bannerAds(FragmentActivity context, ViewGroup group,String key, Handler handler);
    public abstract void bannerExit(FragmentActivity context,ViewGroup group,String key);

}
