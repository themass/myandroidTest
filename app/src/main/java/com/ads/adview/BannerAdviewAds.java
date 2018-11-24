package com.ads.adview;

import android.content.Context;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.ViewGroup;

import com.ads.base.AdsContext;
import com.ads.base.BannerInter;
import com.kyview.interfaces.AdViewBannerListener;
import com.kyview.manager.AdViewBannerManager;

import java.net.ContentHandler;

/**
 * Created by themass on 2017/9/20.
 */

public class BannerAdviewAds extends BannerInter {
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_SPREAD;
    }
    @Override
    public void bannerAds(final Context context, final ViewGroup group, final String key, final Handler handler){
        final View view = AdViewBannerManager.getInstance(context).getAdViewLayout(context, key);
        if (view != null) {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) {
                parent.removeAllViews();
            }
            view.setTag(key);
        }else{
            return ;
        }
        group.addView(view);
        try{
            AdViewBannerManager.getInstance(context).requestAd(context, key, new AdViewBannerListener() {

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
                    group.setVisibility(View.GONE);
                }

                @Override
                public void onAdFailed(String s) {
                    noAds(context,handler, AdsContext.AdsFrom.ADVIEW);
                    group.setVisibility(View.GONE);
                }

                @Override
                public void onAdReady(String s) {
                   readyAds(context,handler, AdsContext.AdsFrom.ADVIEW);

                }
            });
        } catch (Throwable e) {
            noAds(context,handler, AdsContext.AdsFrom.ADVIEW);
         }
    }
    @Override
    public void bannerExit(FragmentActivity context, ViewGroup group, final String key){
        group.removeView(group.findViewWithTag(key));
    }


}
