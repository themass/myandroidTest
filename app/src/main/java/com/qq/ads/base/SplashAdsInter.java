package com.qq.ads.base;

import android.content.Context;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.widget.RelativeLayout;

/**
 * Created by themass on 2017/9/20.
 */

public abstract class SplashAdsInter extends AdsEventAdaptor implements BaseAdsInter{
    public  abstract void lanchExit(Context context,RelativeLayout group);
    public  abstract void launchAds(FragmentActivity mContext, RelativeLayout group, RelativeLayout skipView, Handler handler);
}
