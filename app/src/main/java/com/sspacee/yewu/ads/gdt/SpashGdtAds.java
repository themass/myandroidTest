package com.sspacee.yewu.ads.gdt;

import android.content.Context;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.widget.RelativeLayout;

import com.qq.e.ads.splash.SplashAD;
import com.qq.e.ads.splash.SplashADListener;
import com.qq.e.comm.util.AdError;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.SplashAdsInter;

/**
 * Created by themass on 2017/9/20.
 */

public class SpashGdtAds extends SplashAdsInter {
    public  void lanchExit(Context context, RelativeLayout group){}
    public  void launchAds(final FragmentActivity context, RelativeLayout group, RelativeLayout skipView, final Handler handler){
        SplashAD splashAD = new SplashAD(context, group, skipView, GdtConstants.APPID, GdtConstants.spashId, new SplashADListener(){
            @Override
            public void onADDismissed() {
                    closeAds(context,handler, AdsContext.AdsFrom.GDT);
            }

            @Override
            public void onNoAD(AdError adError) {
                noAds(context,handler, AdsContext.AdsFrom.GDT);
            }

            @Override
            public void onADPresent() {
                displayAds(context,handler, AdsContext.AdsFrom.GDT);
            }

            @Override
            public void onADClicked() {
                clickAds(context,handler, AdsContext.AdsFrom.GDT);
            }

            @Override
            public void onADTick(long l) {
                LogUtil.i("onADTick:"+l);
            }
        }, 0);
    }
}
