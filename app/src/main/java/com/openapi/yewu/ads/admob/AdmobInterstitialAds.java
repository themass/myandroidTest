package com.openapi.yewu.ads.admob;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.openapi.common.util.LogUtil;
import com.openapi.myapp.constant.Constants;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.InterstitialAdsInter;

/**
 * Created by dengt on 2017/9/20.
 */

public class AdmobInterstitialAds extends InterstitialAdsInter {
    private InterstitialAd mInterstitialAd = null;
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_INTERSTITIAL;
    }
    @Override
    public void interstitialAds(final Context context, final Handler handler,final String key, final boolean score,final int count){
        AdRequest adRequest = new AdRequest.Builder().build();
        try{
        InterstitialAd.load(context, Constants.ADMOB_INTER_ID, adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        LogUtil.i("admob interstitialAd ad onAdLoaded "+interstitialAd.getAdUnitId());
                        mInterstitialAd = interstitialAd;
                        mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback(){
                            @Override
                            public void onAdClicked() {
                                // Called when a click is recorded for an ad.
                                LogUtil.i( "admob interstitialAd Ad was clicked.");
                                clickAds(context, handler, AdsContext.AdsFrom.ADMOB);
                            }

                            @Override
                            public void onAdDismissedFullScreenContent() {
                                // Called when ad is dismissed.
                                // Set the ad reference to null so you don't show the ad a second time.
                                LogUtil.i( "admob interstitialAd Ad dismissed fullscreen content.");
                                mInterstitialAd = null;
                                closeAds(context,handler, AdsContext.AdsFrom.ADMOB);
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError adError) {
                                // Called when ad fails to show.
                                LogUtil.i( "admob interstitialAd Ad failed to show fullscreen content.");
                                mInterstitialAd = null;
                                noAds(context,handler, AdsContext.AdsFrom.ADMOB,count);
                            }

                            @Override
                            public void onAdImpression() {
                                // Called when an impression is recorded for an ad.
                                LogUtil.i(  "admob interstitialAd Ad recorded an impression.");
//                                displayAds(context,handler, AdsContext.AdsFrom.ADMOB);
                            }

                            @Override
                            public void onAdShowedFullScreenContent() {
                                // Called when ad is shown.
                                LogUtil.i(  "admob interstitialAd Ad showed fullscreen content.");
                                displayAds(context,handler, AdsContext.AdsFrom.ADMOB);
                            }
                        });
                        mInterstitialAd.show((Activity) context);
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        // Handle the error
                        LogUtil.i("admob interstitialAd  onAdFailedToLoad "+loadAdError.toString());
                        mInterstitialAd = null;
                        noAds(context,handler, AdsContext.AdsFrom.ADMOB,count);
                    }
                });
        } catch (Throwable e) {
            noAds(context,handler, AdsContext.AdsFrom.ADMOB,count);
            LogUtil.e(e);
        }
    }
    @Override
    public void interstitialExit(Context context,String key){

    }
}
