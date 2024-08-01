package com.openai.ads.yewu.ads;

import android.content.Context;

import com.openai.ads.R;
import com.openai.commonutil.StaticDataUtil;
import com.openai.commonutil.SystemUtils;
import com.openai.commonutil.ToastUtil;
import com.openai.ads.yewu.ads.base.AdsContext;
import com.openai.ads.yewu.ads.base.AdsManager;


/**
 * Created by dengt on 2017/9/9.
 */

public class AdsPopStrategy {
    private static long lastToastShow = 0;
    public static int count = 0;

    public static void clickAdsShowBtn(Context context) {
        if (SystemUtils.isApkDebugable(context)) {
            AdsManager.getInstans().showVideo(context);
        }
        if (count++ > 5) {
            return;
        }
        AdsContext.showRand(context, AdsContext.getNext());

        Long lastClickTime = StaticDataUtil.get(Constants.SCORE_CLICK, Long.class, 0l);
        long curent = System.currentTimeMillis();
        long interval = curent - lastClickTime;
        StaticDataUtil.add(Constants.SCORE_CLICK, System.currentTimeMillis());
        if ((interval / 1000) < Constants.SCORE_CLICK_INTERVAL) {
            if ((curent - lastToastShow) / 1000 >= Constants.SCORE_CLICK_INTERVAL) {
                ToastUtil.showShort(R.string.tab_fb_click_fast);
                lastToastShow = curent;
            }
            return;
        }
    }

    public static boolean clickAdsClickBtn(Context context) {
        Long lastClickTime = StaticDataUtil.get(Constants.SCORE_CLICK_CLICK, Long.class, 0l);
        long curent = System.currentTimeMillis();
        long interval = curent - lastClickTime;
        StaticDataUtil.add(Constants.SCORE_CLICK_CLICK, System.currentTimeMillis());
        if ((interval / 1000) < Constants.SCORE_CLICK_INTERVAL) {
            if ((curent - lastToastShow) / 1000 >= Constants.SCORE_CLICK_INTERVAL) {
                ToastUtil.showShort(R.string.tab_fb_click_fast);
                lastToastShow = curent;
            }
            return false;
        }
        return true;
    }
}
