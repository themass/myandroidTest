package com.openapi.ks.myapp.data;

import android.content.Context;

import com.openapi.commons.common.util.SystemUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.commons.yewu.ads.base.AdsManager;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.constant.Constants;

/**
 * Created by openapi on 2017/9/9.
 */

public class AdsPopStrategy {
    private static long lastToastShow=0;
    public static void clickAdsShowBtn(Context context){
        if(SystemUtils.isApkDebugable(context)){
            AdsManager.getInstans().showVideo(context);
        }
        AdsContext.showNextAbs(context);
        Long lastClickTime = StaticDataUtil.get(Constants.SCORE_CLICK, Long.class, 0l);
        long curent = System.currentTimeMillis();
        long interval = curent - lastClickTime;
        StaticDataUtil.add(Constants.SCORE_CLICK, System.currentTimeMillis());
        if ((interval / 1000) < Constants.SCORE_CLICK_INTERVAL) {
            if ((curent - lastToastShow) / 1000 >= Constants.SCORE_CLICK_INTERVAL) {
                ToastUtil.showShort( R.string.tab_fb_click_fast);
                lastToastShow = curent;
            }
            return;
        }
    }
}
