package com.timeline.vpn.ads.banner;

import android.app.Activity;
import android.os.Handler;

import com.timeline.vpn.ads.BaseAdsController;
import com.timeline.vpn.common.util.LogUtil;

/**
 * Created by gqli on 2016/3/22.
 */
public abstract class BannerAdsController extends BaseAdsController {
    public BannerAdsController(Activity context,Handler handler){
        super(context,handler);
        LogUtil.i("new "+getClass().getSimpleName());
    }
}
