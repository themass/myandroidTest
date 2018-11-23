package com.ads.base;

import android.content.Context;
import android.os.Handler;

/**
 * Created by themass on 2017/9/21.
 */

public abstract class NativeAdsInter extends AdsEventAdaptor implements BaseAdsInter{
    public  abstract void showNative(Context context, Handler handler, NativeAdsReadyListener listener);
}
