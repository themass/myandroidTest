package com.sspacee.yewu.ads.adview;

import android.content.Context;
import android.os.Handler;

import com.kyview.interfaces.AdViewNativeListener;
import com.kyview.manager.AdViewNativeManager;
import com.kyview.natives.NativeAdInfo;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.NativeAdsInter;
import com.sspacee.yewu.ads.base.NativeAdsReadyListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by themass on 2017/9/21.
 */

public class NativeAdviewAds extends NativeAdsInter{
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_NATIVE;
    }
    @Override
    public  void showNative(final Context context, final Handler handler, final NativeAdsReadyListener listener) {
        try {
            AdViewNativeManager.getInstance(context).requestAd(context, AdviewConstant.ADS_ADVIEW_KEY1, 2, new AdViewNativeListener() {

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
            LogUtil.e("原声广告fail：", e);
        }
    }
}