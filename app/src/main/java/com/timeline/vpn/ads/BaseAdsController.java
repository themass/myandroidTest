package com.timeline.vpn.ads;

import android.app.Activity;
import android.os.Handler;

/**
 * Created by gqli on 2016/3/23.
 */
public abstract class BaseAdsController {
    protected Handler mHandler;
    protected Activity mContext;
    public BaseAdsController(Activity context,Handler handler){
        this.mContext=context;
        this.mHandler=handler;
    }
    public abstract void showAds();
}
