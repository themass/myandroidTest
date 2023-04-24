package com.openapi.yewu.ads.admob;

import android.app.Activity;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.openapi.common.util.LogUtil;
import com.openapi.myapp.constant.Constants;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.BannerInter;
import com.openapi.yewu.ads.mobvista.MobivistaBannerAds;

/**
 * Created by dengt on 2017/9/20.
 */

public class AdmobBannerAds extends BannerInter {
    private MobivistaBannerAds mobvBanner = new MobivistaBannerAds();
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_BANNER;
    }
    @Override
    public void bannerAds(final FragmentActivity context, final ViewGroup group, final String key, final Handler handler){
        AdView adView = new AdView(context);
        adView.setAdUnitId(Constants.ADMOB_BANNER_ID);
        if (adView != null) {
            ViewGroup parent = (ViewGroup) adView.getParent();
            if (parent != null) {
                parent.removeAllViews();
            }
            adView.setTag(key);
        }else{
            LogUtil.e("admob banner banner is error :"+key);
            return ;
        }
        try{
            LogUtil.i("admob banner  req :"+key);
            group.addView(adView);
            AdRequest adRequest = new AdRequest.Builder().build();

            AdSize adSize = getAdSize(context);
            // Step 4 - Set the adaptive ad size on the ad view.
            adView.setAdSize(adSize);

            // Step 5 - Start loading the ad in the background.
            adView.loadAd(adRequest);
            adView.setAdListener(new AdListener(){
                @Override
                public void onAdClicked() {
                    LogUtil.i("admob banner  onAdClicked ");
                    if(!AdsContext.hasClick(context,"banner")) {
                        clickAds(context, handler, AdsContext.AdsFrom.ADMOB);
                    }
                }

                @Override
                public void onAdClosed() {
                    LogUtil.i("admob banner  onAdClosed ");
                    closeAds(context,handler, AdsContext.AdsFrom.ADMOB);
                    group.setVisibility(View.GONE);
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    LogUtil.i("admob banner onAdFailedToLoad "+loadAdError.toString());
                    noAds(context,handler, AdsContext.AdsFrom.ADMOB,0);
    //                   group.setVisibility(View.GONE);
                    mobvBanner.bannerAds(context,group,key,handler);
                }

                @Override
                public void onAdImpression() {
                    LogUtil.i("admob banner  onAdImpression ");
                }

                @Override
                public void onAdLoaded() {
                    LogUtil.i("admob banner nAdLoaded ");
                    readyAds(context,handler, AdsContext.AdsFrom.ADMOB);
                }

                @Override
                public void onAdOpened() {
                    LogUtil.i("admob banner  onAdOpened ");
                    displayAds(context,handler, AdsContext.AdsFrom.ADMOB);
                }

            });
        } catch (Throwable e) {
            noAds(context,handler, AdsContext.AdsFrom.ADVIEW,0);
            mobvBanner.bannerAds(context,group,key,handler);
            LogUtil.e(e);
         }
    }
    private AdSize getAdSize(Activity context) {
        // Step 2 - Determine the screen width (less decorations) to use for the ad width.
        Display display = context.getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        float widthPixels = outMetrics.widthPixels;
        float density = outMetrics.density;

        int adWidth = (int) (widthPixels / density);

        // Step 3 - Get adaptive ad size and return for setting on the ad view.
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth);
    }
    @Override
    public void bannerExit(FragmentActivity context, ViewGroup group, final String key){
        LogUtil.i("admob banner bannerExit:"+key);
        group.removeView(group.findViewWithTag(key));
    }


}
