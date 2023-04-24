package com.openapi.yewu.ads.admob;

import android.content.Context;
import android.os.Handler;
import android.widget.RelativeLayout;

import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.openapi.common.util.EventBusUtil;
import com.openapi.common.util.LogUtil;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.config.SplashAdDissmisEvent;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.SplashAdsInter;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.openapi.yewu.ads.config.LaunchAdsNext;


/**
 * Created by dengt on 2017/9/20.
 */

public class AdmobSplashAds extends SplashAdsInter {
    private AppOpenAd appOpenAd;
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_SPREAD;
    }
    @Override
    public  void lanchExit(Context context,RelativeLayout group){
//        AdViewSpreadManager.getInstance(context).destroySpread(ADS_ADVIEW_KEY);
    }
    @Override
    public  void launchAds(final FragmentActivity context, RelativeLayout group, RelativeLayout skipView, final Handler handler){
        try{
            LogUtil.i("admob flash load ");
            AdManagerAdRequest request = new AdManagerAdRequest.Builder().build();
            AppOpenAd.load(
                    context,
                    Constants.ADMOB_SPLASH_ID,
                    request,
                    new AppOpenAd.AppOpenAdLoadCallback() {
                        /**
                         * Called when an app open ad has loaded.
                         *
                         * @param ad the loaded app open ad.
                         */
                        @Override
                        public void onAdLoaded(AppOpenAd ad) {
                            LogUtil.i("admob flash onAdLoaded "+ad.getAdUnitId());
                            appOpenAd = ad;
                            appOpenAd.setFullScreenContentCallback(
                                    new FullScreenContentCallback() {
                                        /** Called when full screen content is dismissed. */
                                        @Override
                                        public void onAdDismissedFullScreenContent() {
                                            // Set the reference to null so isAdAvailable() returns false.
                                            LogUtil.i("admob flash onAdDismissedFullScreenContent");
                                            appOpenAd = null;
                                            EventBusUtil.getEventBus().post(new SplashAdDissmisEvent(AdsContext.AdsFrom.ADMOB.desc));
                                        }

                                        /** Called when fullscreen content failed to show. */
                                        @Override
                                        public void onAdFailedToShowFullScreenContent(AdError adError) {
                                            LogUtil.i("admob flash onAdFailedToShowFullScreenContent "+adError.toString());
                                            appOpenAd = null;
                                        }

                                        /** Called when fullscreen content is shown. */
                                        @Override
                                        public void onAdShowedFullScreenContent() {
                                            LogUtil.i("admob flash onAdShowedFullScreenContent ");
                                        }
                                    });
                            appOpenAd.show(context);
                        }

                        /**
                         * Called when an app open ad has failed to load.
                         *
                         * @param loadAdError the error.
                         */
                        @Override
                        public void onAdFailedToLoad(LoadAdError loadAdError) {
                            LogUtil.i("admob splash onAdFailedToLoad "+loadAdError.toString());
                            EventBusUtil.getEventBus().post(new LaunchAdsNext(AdsContext.AdsFrom.ADMOB));
                        }
                    });
        } catch (Throwable e) {
            noAds(context,handler, AdsContext.AdsFrom.ADMOB,0);
            LogUtil.e("admob flash "+e);
        }
    }
}
