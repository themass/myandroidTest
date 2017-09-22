package com.timeline.vpn.data;

import android.content.Context;
import android.widget.Toast;

import com.sspacee.common.util.SystemUtils;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;

/**
 * Created by themass on 2017/9/9.
 */

public class AdsPopStrategy {
    private static long lastToastShow=0;
    public static void clickAdsShowBtn(Context context){
        if(SystemUtils.isApkDebugable(context)){
            if(!AdsManager.getInstans().showVideo(context))
                AdsManager.getInstans().showInterstitialAds(context, AdsContext.Categrey.CATEGREY_3,true);
            return;
        }
        Long lastClickTime = StaticDataUtil.get(Constants.SCORE_CLICK, Long.class, 0l);
        long curent = System.currentTimeMillis();
        long interval = curent - lastClickTime;
        StaticDataUtil.add(Constants.SCORE_CLICK, System.currentTimeMillis());
        if ((interval / 1000) < Constants.SCORE_CLICK_INTERVAL) {
            if ((curent - lastToastShow) / 1000 >= Constants.SCORE_CLICK_INTERVAL) {
                Toast.makeText(context, R.string.tab_fb_click_fast, Toast.LENGTH_SHORT).show();
                lastToastShow = curent;
            }
            return;
        }
        if(AdsContext.rateShow() && !AdsManager.getInstans().showVideo(context))
            AdsManager.getInstans().showInterstitialAds(context, AdsContext.Categrey.CATEGREY_3,true);
    }
}
