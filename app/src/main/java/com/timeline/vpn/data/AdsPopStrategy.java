package com.timeline.vpn.data;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.widget.Toast;

import com.sspacee.common.util.Md5;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.yewu.ads.adview.AdsAdview;
import com.sspacee.yewu.net.NetUtils;
import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.task.ScoreTask;

/**
 * Created by themass on 2017/9/9.
 */

public class AdsPopStrategy {
    private static long lastToastShow=0;
    public static void clickAdsShowBtn(Context context, Handler handler){
        if(SystemUtils.isApkDebugable(context)){
            AdsAdview.videoAdsReq(context, handler);
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
        if(StaticDataUtil.get(Constants.VPN_STATUS, Intent.class)==null ){
            if(NetUtils.isWifi(context)){
                if(SystemUtils.isApkDebugable(context)){
                    AdsAdview.videoAdsReq(context, handler);
                }else {
                    if (Md5.getRandom(10) % 2 == 0) {
                        AdsAdview.videoAdsReq(context, handler);
                    } else {
                        AdsAdview.interstitialAds(context, handler);
                    }
                }
            }else{
                AdsAdview.interstitialAds(context, handler);
            }

            String msg = context.getResources().getString(R.string.tab_fb_click) + Constants.ADS_SHOW_SCORE;
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            ScoreTask.start(context, Constants.ADS_SHOW_SCORE);
        }else{
            Toast.makeText(context, R.string.tab_fb_click_wifi, Toast.LENGTH_SHORT).show();
        }
    }
}
