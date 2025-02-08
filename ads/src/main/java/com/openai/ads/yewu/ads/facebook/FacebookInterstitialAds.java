package com.openai.ads.yewu.ads.facebook;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;

import com.facebook.ads.Ad;
import com.facebook.ads.AdError;
import com.facebook.ads.InterstitialAd;
import com.facebook.ads.InterstitialAdListener;
import com.openai.ads.yewu.ads.Constants;
import com.openai.ads.yewu.ads.base.AdsContext;
import com.openai.ads.yewu.ads.base.InterstitialAdsInter;
import com.openai.commonutil.LogUtil;

/**
 * Created by dengt on 2017/9/20.
 */

public class FacebookInterstitialAds extends InterstitialAdsInter {
    private InterstitialAd mInterstitialAd = null;

    @Override
    protected AdsContext.AdsType getAdsType() {
        return AdsContext.AdsType.ADS_TYPE_INTERSTITIAL;
    }

    @Override
    public void interstitialAds(final Context context, final Handler handler, final String key, final boolean score, final int count) {
        mInterstitialAd = new InterstitialAd(context, Constants.FACEBOOK_INTER_ID);
        // Create listeners for the Interstitial Ad
        InterstitialAdListener interstitialAdListener = new InterstitialAdListener() {
            @Override
            public void onInterstitialDisplayed(Ad ad) {
                // Called when ad is shown.
                LogUtil.i("facebook interstitialAd Ad showed fullscreen content.");
                displayAds(context, handler, AdsContext.AdsFrom.FACEBOOK);
            }

            @Override
            public void onInterstitialDismissed(Ad ad) {
                LogUtil.i("facebook interstitialAd Ad dismissed fullscreen content.");
                mInterstitialAd = null;
                closeAds(context, handler, AdsContext.AdsFrom.FACEBOOK);
            }

            @Override
            public void onError(Ad ad, AdError adError) {
                LogUtil.i("facebook interstitialAd Ad failed to show fullscreen content."+adError.getErrorMessage());
                mInterstitialAd = null;
                noAds(context, handler, AdsContext.AdsFrom.FACEBOOK, count);
            }

            @Override
            public void onAdLoaded(Ad ad) {
                LogUtil.i("facebook interstitialAd ad onAdLoaded " + ad.getPlacementId());
                mInterstitialAd.show();
            }

            @Override
            public void onAdClicked(Ad ad) {
                LogUtil.i("facebook interstitialAd Ad was clicked.");
                clickAds(context, handler, AdsContext.AdsFrom.FACEBOOK);
            }

            @Override
            public void onLoggingImpression(Ad ad) {
                LogUtil.i("facebook interstitialAd Ad recorded an impression.");

            }
        };
        mInterstitialAd.loadAd(
                mInterstitialAd.buildLoadAdConfig()
                        .withAdListener(interstitialAdListener)
                        .build());
    }

    @Override
    public void interstitialExit(Context context, String key) {

    }
}
