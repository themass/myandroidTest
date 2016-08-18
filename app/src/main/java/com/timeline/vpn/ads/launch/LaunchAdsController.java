package com.timeline.vpn.ads.launch;

import android.app.Activity;
import android.os.Handler;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.timeline.vpn.ads.BaseAdsController;
import com.timeline.vpn.common.util.LogUtil;

/**
 * Created by gqli on 2016/3/22.
 */
public abstract class LaunchAdsController extends BaseAdsController {
    protected final ViewGroup adsGroup;
    protected final ImageButton ibSkip;
    public LaunchAdsController(Activity context,ViewGroup adsGroup,Handler handler,ImageButton ibSkip){
        super(context,handler);
        this.adsGroup = adsGroup;
        this.ibSkip = ibSkip;
        LogUtil.i("new "+getClass().getSimpleName());
    }
}
