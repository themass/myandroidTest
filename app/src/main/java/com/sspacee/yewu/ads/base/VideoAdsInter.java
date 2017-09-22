package com.sspacee.yewu.ads.base;

import android.content.Context;
import android.os.Handler;

/**
 * Created by themass on 2017/9/22.
 */

public abstract class VideoAdsInter extends AdsEventAdaptor implements BaseAdsInter{
    public abstract void reqVideo( Context context,  Handler handler);
    public abstract void showVideo(Context context );
    public abstract void exitVideo(Context context);
}
