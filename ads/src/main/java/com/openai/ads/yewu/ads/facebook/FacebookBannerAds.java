package com.openai.ads.yewu.ads.facebook;

import android.app.Activity;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.openai.ads.yewu.ads.Constants;
import com.openai.ads.yewu.ads.base.AdsContext;
import com.openai.ads.yewu.ads.base.BannerInter;
import com.openai.commonutil.LogUtil;
import com.facebook.ads.*;

/**
 * Created by dengt on 2017/9/20.
 */

public class FacebookBannerAds extends BannerInter {
    @Override
    protected AdsContext.AdsType getAdsType() {
        return AdsContext.AdsType.ADS_TYPE_BANNER;
    }

    @Override
    public void bannerAds(final FragmentActivity context, final ViewGroup group, final String key, final Handler handler) {
        AdView adView = new AdView(context, Constants.adviewToFacebookBanner.get(key), AdSize.BANNER_HEIGHT_50);
        if (adView != null) {
            ViewGroup parent = (ViewGroup) adView.getParent();
            if (parent != null) {
                parent.removeAllViews();
            }
            adView.setTag(key);
        } else {
            LogUtil.e("facebook banner banner is error :" + key);
            return;
        }
        try {
            LogUtil.i("facebook banner  req :" + key);
            group.addView(adView);
            adView.loadAd(adView.buildLoadAdConfig().withAdListener(new AdListener() {
                @Override
                public void onError(Ad ad, AdError adError) {
                    LogUtil.i("facebook banner onAdFailedToLoad code:" + adError.getErrorCode()+" ;msg:"+adError.getErrorMessage());
                    noAds(context, handler, AdsContext.AdsFrom.FACEBOOK, 0);
                }

                @Override
                public void onAdLoaded(Ad ad) {
                    LogUtil.i("facebook banner nAdLoaded ");
                    readyAds(context, handler, AdsContext.AdsFrom.FACEBOOK);
                }

                @Override
                public void onAdClicked(Ad ad) {
                    LogUtil.i("facebook banner  onAdClicked ");
                    if (!AdsContext.hasClick(context, "banner")) {
                        clickAds(context, handler, AdsContext.AdsFrom.FACEBOOK);
                    }
                }

                @Override
                public void onLoggingImpression(Ad ad) {
                    LogUtil.i("facebook banner  onAdOpened ");
                    displayAds(context, handler, AdsContext.AdsFrom.FACEBOOK);
                }
            }).build());
        } catch (Throwable e) {
            noAds(context, handler, AdsContext.AdsFrom.FACEBOOK, 0);
            LogUtil.e(e);
        }
    }

    @Override
    public void bannerExit(FragmentActivity context, ViewGroup group, final String key) {
        LogUtil.i("facebook banner bannerExit:" + key);
        group.removeView(group.findViewWithTag(key));
    }


}
