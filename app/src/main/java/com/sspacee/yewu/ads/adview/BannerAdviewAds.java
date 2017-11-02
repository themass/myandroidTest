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

/**
 * Created by themass on 2017/9/20.
 */

public class BannerAdviewAds extends BannerInter {
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_SPREAD;
    }
    @Override
    public void bannerAds(final FragmentActivity context, final ViewGroup group,final String key, final Handler handler){
        final View view = AdViewBannerManager.getInstance(context).getAdViewLayout(context, key);
        if (view != null) {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) {
                parent.removeAllViews();
            }
            view.setTag(key);
        }else{
            LogUtil.e(" banner is error :"+key);
            return ;
        }
        LogUtil.i(" banner req :"+key);
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
            LogUtil.e(e);
         }
    }
    @Override
    public void bannerExit(FragmentActivity context,ViewGroup group,final String key){
        LogUtil.i("bannerExit:"+key);
        group.removeView(group.findViewWithTag(key));
    }


}
