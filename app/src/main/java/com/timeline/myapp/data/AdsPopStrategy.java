package com.timeline.myapp.data;

import android.content.Context;

import com.sspacee.common.util.Md5;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.timeline.sexfree1.R;
import com.timeline.myapp.base.MyApplication;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.ui.sound.VideoShowActivity;
import com.timeline.sexfree1.ui.main.MainFragmentViewPage;

/**
 * Created by themass on 2017/9/9.
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
