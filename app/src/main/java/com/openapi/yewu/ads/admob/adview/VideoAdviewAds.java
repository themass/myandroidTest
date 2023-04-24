package com.openapi.yewu.ads.admob.adview;

import android.content.Context;
import android.os.Handler;

import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.VideoAdsInter;

/**
 * Created by dengt on 2017/9/22.
 */

public class VideoAdviewAds extends VideoAdsInter {
    public boolean isReq = false;
    @Override
    public void reqVideo(final Context context, final Handler handler){
//        AdViewVideoManager.getInstance(context).requestAd(context, ADS_ADVIEW_KEY, new AdViewVideoListener(){
//            @Override
//            public void onAdFailed(String arg0) {
//                noAds(context,handler, AdsContext.AdsFrom.ADVIEW,0);
//            }
//
//            @Override
//            public void onAdRecieved(String arg0) {
//            }
//
//            @Override
//            public void onAdClose(String arg0) {
//                closeAds(context,handler, AdsContext.AdsFrom.ADVIEW);
//            }
//
//            @Override
//            public void onAdReady(String s) {
//                readyAds(context,handler, AdsContext.AdsFrom.ADVIEW);
//                isReq = true;
//            }
//
//            @Override
//            public void onAdPlayEnd(String arg0, Boolean arg1) {
//                displayAds(context,handler, AdsContext.AdsFrom.ADVIEW);
//            }
//
//            @Override
//            public void onAdPlayStart(String arg0) {
//            }
//        });
    }
    @Override
    public void showVideo(final Context context){
//        AdViewVideoManager.getInstance(context).playVideo(context, ADS_ADVIEW_KEY);
    }
    @Override
    public void exitVideo(Context context){
    }

    @Override
    protected AdsContext.AdsType getAdsType() {
        return AdsContext.AdsType.ADS_TYPE_VIDEO;
    }
}
