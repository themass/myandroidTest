package com.sspacee.yewu.ads.adview;

import android.content.Context;

/**
 * Created by themass on 2017/1/1.
 */
public interface AdsController {
    public void showAds(Context context);

    public void hidenAds(Context context);

    public boolean needShow(Context context);
}
