package com.sspacee.yewu.ads.adview;

import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.ViewGroup;

import com.kyview.interfaces.AdViewBannerListener;
import com.kyview.manager.AdViewBannerManager;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.BannerInter;

import static com.sspacee.yewu.ads.adview.AdviewConstant.ADS_ADVIEW_KEY;

/**
 * Created by themass on 2017/9/20.
 */

public class BannerAdviewAds extends BannerInter {
    @Override
    public void bannerAds(final FragmentActivity context, final ViewGroup group, final Handler handler){
        final View view = AdViewBannerManager.getInstance(context).getAdViewLayout(context, ADS_ADVIEW_KEY);
        if (view != null) {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) {
                parent.removeAllViews();
            }
            view.setTag(ADS_ADVIEW_KEY);
        }
        try{
            AdViewBannerManager.getInstance(context).requestAd(context, ADS_ADVIEW_KEY, new AdViewBannerListener() {

                @Override
                public void onAdClick(String s) {
                    clickAds(context,handler, AdsContext.AdsFrom.ADVIEW);
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
                public void onAdFailed(String s) {
                    noAds(context,handler, AdsContext.AdsFrom.ADVIEW);
                }

                @Override
                public void onAdReady(String s) {
                   readyAds(context,handler, AdsContext.AdsFrom.ADVIEW);
                    group.addView(view);
                }
            });
        } catch (Throwable e) {
            noAds(context,handler, AdsContext.AdsFrom.ADVIEW);
            LogUtil.e(e);
         }
    }
    @Override
    public void bannerExit(FragmentActivity context,ViewGroup group){
        group.removeView(group.findViewWithTag(ADS_ADVIEW_KEY));
    }
}
