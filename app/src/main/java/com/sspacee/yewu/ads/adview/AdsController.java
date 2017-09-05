package com.sspacee.yewu.ads.adview;

import android.content.Context;

/**
 * Created by themass on 2017/1/1.
 */
public interface AdsController {
    void showAds(Context context);

    void hidenAds(Context context);

    boolean needShow(Context context);
}
