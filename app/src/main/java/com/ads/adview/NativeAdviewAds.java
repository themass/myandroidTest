package com.ads.adview;

import android.content.Context;
import android.os.Handler;

import com.ads.base.AdsContext;
import com.ads.base.NativeAdsInter;
import com.ads.base.NativeAdsReadyListener;
import com.kyview.interfaces.AdViewNativeListener;
import com.kyview.manager.AdViewNativeManager;
import com.kyview.natives.NativeAdInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by dengt on 2017/9/21.
 */

public class NativeAdviewAds extends NativeAdsInter {
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_NATIVE;
    }
    @Override
    public  void showNative(final Context context, final Handler handler, final NativeAdsReadyListener listener, String key) {
        try {
            AdViewNativeManager.getInstance(context).requestAd(context, key, 2, new AdViewNativeListener() {

                @Override
                public void onAdFailed(String s) {
                    noAds(context,handler, AdsContext.AdsFrom.ADVIEW);
                 }

                @Override
                public void onAdRecieved(String s, ArrayList arrayList) {
                    readyAds(context,handler, AdsContext.AdsFrom.ADVIEW);
                    if (listener != null) {
                        listener.onAdRecieved((List<NativeAdInfo>) arrayList);
                    }
                }

                @Override
                public void onAdStatusChanged(String s, int i) {
                    clickAds(context,handler, AdsContext.AdsFrom.ADVIEW);
                }
            }); //设置原生回调接口
        } catch (Throwable e) {
            noAds(context,handler, AdsContext.AdsFrom.ADVIEW);
        }
    }
}
