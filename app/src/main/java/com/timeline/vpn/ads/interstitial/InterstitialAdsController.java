package com.timeline.vpn.ads.interstitial;

import android.app.Activity;
import android.os.Handler;

import com.timeline.vpn.ads.BaseAdsController;
import com.timeline.vpn.common.util.LogUtil;

/**
 * Created by gqli on 2016/3/23.
 */
public abstract class InterstitialAdsController extends BaseAdsController {
    public InterstitialAdsController(Activity context,Handler handler){
        super(context,handler);
        LogUtil.i("new "+getClass().getSimpleName());
    }
    abstract public boolean close();
}
