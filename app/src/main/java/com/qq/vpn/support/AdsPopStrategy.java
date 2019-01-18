package com.qq.vpn.support;

import android.content.Context;

import com.qq.Constants;
import com.qq.ads.base.AdsContext;
import com.qq.ads.base.AdsManager;
import com.qq.ext.util.SystemUtils;
import com.qq.ext.util.ToastUtil;
import com.qq.network.R;

/**
 * Created by dengt on 2017/9/9.
 */

public class AdsPopStrategy {
    private static long lastToastShow=0;
    public static void clickAdsShowBtn(Context context){
        AdsContext.showNextAbs(context, AdsContext.Categrey.CATEGREY_VPN2);
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
    public static boolean clickAdsClickBtn(Context context){
        Long lastClickTime = StaticDataUtil.get(Constants.SCORE_CLICK_CLICK, Long.class, 0l);
        long curent = System.currentTimeMillis();
        long interval = curent - lastClickTime;
        StaticDataUtil.add(Constants.SCORE_CLICK_CLICK, System.currentTimeMillis());
        if ((interval / 1000) < Constants.SCORE_CLICK_INTERVAL) {
            if ((curent - lastToastShow) / 1000 >= Constants.SCORE_CLICK_INTERVAL) {
                ToastUtil.showShort( R.string.tab_fb_click_fast);
                lastToastShow = curent;
            }
            return false;
        }
        return true;
    }
}
