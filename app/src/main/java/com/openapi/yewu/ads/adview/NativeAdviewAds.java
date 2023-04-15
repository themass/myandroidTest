package com.openapi.yewu.ads.adview;

import android.content.Context;
import android.os.Handler;

import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.NativeAdsInter;
import com.openapi.yewu.ads.base.NativeAdsReadyListener;

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
//        try {
//            AdViewNativeManager.getInstance(context).requestAd(context, key, 2, new AdViewNativeListener() {
//
//                @Override
//                public void onAdFailed(String s) {
//                    noAds(context,handler, AdsContext.AdsFrom.ADVIEW,0);
//                 }
//
//                @Override
//                public void onAdRecieved(String s, ArrayList arrayList) {
//                    readyAds(context,handler, AdsContext.AdsFrom.ADVIEW);
//                    if (listener != null) {
//                        listener.onAdRecieved((List<NativeAdInfo>) arrayList);
//                    }
//                }
//
//                @Override
//                public void onAdStatusChanged(String s, int i) {
//                    clickAds(context,handler, AdsContext.AdsFrom.ADVIEW);
//                }
//            }); //设置原生回调接口
//        } catch (Throwable e) {
//            noAds(context,handler, AdsContext.AdsFrom.ADVIEW,0);
//            LogUtil.e("原声广告fail：", e);
//        }
    }
}
