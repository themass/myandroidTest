package com.ads.adview;

import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import com.ads.base.AdsContext;
import com.ads.base.VideoAdsInter;
import com.kyview.interfaces.AdViewVideoListener;
import com.kyview.manager.AdViewVideoManager;
import com.qq.sexfree.R;

import static com.ads.adview.AdviewConstant.ADS_ADVIEW_KEY2;

/**
 * Created by themass on 2017/9/22.
 */

public class VideoAdviewAds extends VideoAdsInter {
    public boolean isReq = false;
    @Override
    public void reqVideo(final Context context, final Handler handler){
        AdViewVideoManager.getInstance(context).requestAd(context, ADS_ADVIEW_KEY2, new AdViewVideoListener(){
            @Override
            public void onAdFailed(String arg0) {
                noAds(context,handler, AdsContext.AdsFrom.ADVIEW);
            }

            @Override
            public void onAdRecieved(String arg0) {
            }

            @Override
            public void onAdClose(String arg0) {
                closeAds(context,handler, AdsContext.AdsFrom.ADVIEW);
            }

            @Override
            public void onAdReady(String s) {
                readyAds(context,handler, AdsContext.AdsFrom.ADVIEW);
                isReq = true;
            }

            @Override
            public void onAdPlayEnd(String arg0, Boolean arg1) {
                displayAds(context,handler, AdsContext.AdsFrom.ADVIEW);
            }

            @Override
            public void onAdPlayStart(String arg0) {
            }
        });
    }
    @Override
    public void showVideo(final Context context){
        AdViewVideoManager.getInstance(context).playVideo(context, ADS_ADVIEW_KEY2);
    }
    @Override
    public void exitVideo(Context context){
    }

    @Override
    protected AdsContext.AdsType getAdsType() {
        return AdsContext.AdsType.ADS_TYPE_VIDEO;
    }
}
