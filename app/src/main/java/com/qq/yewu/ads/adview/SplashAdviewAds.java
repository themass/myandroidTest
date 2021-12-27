package com.qq.yewu.ads.adview;

import android.content.Context;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.kyview.interfaces.AdViewSpreadListener;
import com.kyview.manager.AdViewSpreadManager;
import com.qq.common.util.LogUtil;
import com.qq.ks.free1.R;
import com.qq.yewu.ads.base.AdsContext;
import com.qq.yewu.ads.base.SplashAdsInter;

import static com.qq.yewu.ads.adview.AdviewConstant.ADS_ADVIEW_KEY;
import static com.qq.yewu.ads.adview.AdviewConstant.adsKeySet;

/**
 * Created by dengt on 2017/9/20.
 */

public class SplashAdviewAds extends SplashAdsInter {
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_SPREAD;
    }
    @Override
    public  void lanchExit(Context context,RelativeLayout group){
        AdViewSpreadManager.getInstance(context).destroySpread(ADS_ADVIEW_KEY);
    }
    @Override
    public  void launchAds(final FragmentActivity context, RelativeLayout group, RelativeLayout skipView, final Handler handler){
        try {
            AdViewSpreadManager.getInstance(context).init(AdviewAdsManager.initConfig, adsKeySet);
            AdViewSpreadManager.getInstance(context).setSpreadLogo(R.drawable.ic_trans_logo);
            AdViewSpreadManager.getInstance(context).request(context, ADS_ADVIEW_KEY, new AdViewSpreadListener() {
                @Override
                public void onAdClick(String s) {
                    if(!AdsContext.hasClick(context,"Splash"+s)) {
                        clickAds(context, handler, AdsContext.AdsFrom.ADVIEW);
                    }
                }

                @Override
                public void onAdDisplay(String s) {
                    displayAds(context,handler, AdsContext.AdsFrom.ADVIEW);
                }

                @Override
                public void onAdClose(String s) {
                    closeAds(context,handler, AdsContext.AdsFrom.ADVIEW);
                }

                @Override
                public void onAdRecieved(String s) {
                    readyAds(context,handler, AdsContext.AdsFrom.ADVIEW);
                }

                @Override
                public void onAdFailed(String s) {
                    noAds(context,handler, AdsContext.AdsFrom.ADVIEW,0);
                }

                @Override
                public void onAdSpreadNotifyCallback(String key, ViewGroup view, int ruleTime, int delayTime) {
                    LogUtil.i("ruleTime:" + ruleTime + ";delayTime:" + delayTime);
                }
            }, group, skipView);
        } catch (Throwable e) {
            noAds(context,handler, AdsContext.AdsFrom.ADVIEW,0);
            LogUtil.e(e);
        }
    }
}
