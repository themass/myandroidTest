package com.qq.yewu.ads.adview;

import android.os.Handler;
import androidx.fragment.app.FragmentActivity;
import android.view.View;
import android.view.ViewGroup;

import com.qq.common.util.LogUtil;
import com.qq.yewu.ads.base.AdsContext;
import com.qq.yewu.ads.base.BannerInter;
import com.qq.yewu.ads.mobvista.BannerMobvAds;

/**
 * Created by dengt on 2017/9/20.
 */

public class BannerAdviewAds extends BannerInter {
    private BannerMobvAds mobvBanner = new BannerMobvAds();
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_BANNER;
    }
    @Override
    public void bannerAds(final FragmentActivity context, final ViewGroup group, final String key, final Handler handler){
//        final View view = AdViewBannerManager.getInstance(context).getAdViewLayout(context, key);
//        if (view != null) {
//            ViewGroup parent = (ViewGroup) view.getParent();
//            if (parent != null) {
//                parent.removeAllViews();
//            }
//            view.setTag(key);
//        }else{
//            LogUtil.e(" banner is error :"+key);
//            return ;
//        }
//        LogUtil.i(" banner req :"+key);
//        group.addView(view);
//        try{
//            AdViewBannerManager.getInstance(context).requestAd(context, key, new AdViewBannerListener() {
//
//                @Override
//                public void onAdClick(String s) {
//                    if(!AdsContext.hasClick(context,"banner"+s)) {
//                        clickAds(context, handler, AdsContext.AdsFrom.ADVIEW);
//                    }
//                }
//
//                @Override
//                public void onAdDisplay(String s) {
//                    displayAds(context,handler, AdsContext.AdsFrom.ADVIEW);
//                }
//
//                @Override
//                public void onAdClose(String s) {
//                    closeAds(context,handler, AdsContext.AdsFrom.ADVIEW);
//                    group.setVisibility(View.GONE);
//                }
//
//                @Override
//                public void onAdFailed(String s) {
//                    noAds(context,handler, AdsContext.AdsFrom.ADVIEW,0);
////                    group.setVisibility(View.GONE);
//                    mobvBanner.bannerAds(context,group,key,handler);
//                }
//
//                @Override
//                public void onAdReady(String s) {
//                   readyAds(context,handler, AdsContext.AdsFrom.ADVIEW);
//                }
//            });
//        } catch (Throwable e) {
//            noAds(context,handler, AdsContext.AdsFrom.ADVIEW,0);
//            mobvBanner.bannerAds(context,group,key,handler);
//            LogUtil.e(e);
//         }
    }
    @Override
    public void bannerExit(FragmentActivity context, ViewGroup group, final String key){
        LogUtil.i("bannerExit:"+key);
        group.removeView(group.findViewWithTag(key));
    }


}
