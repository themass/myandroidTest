package com.openapi.commons.yewu.ads.adview;

import android.content.Context;
import android.os.Handler;

import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.commons.yewu.ads.base.VideoAdsInter;

/**
 * Created by openapi on 2017/9/22.
 */

public class VideoAdviewAds extends VideoAdsInter{
    public boolean isReq = false;
    @Override
    public void reqVideo(final Context context, final Handler handler){
    }
    @Override
    public void showVideo(final Context context){
    }
    @Override
    public void exitVideo(Context context){
    }

    @Override
    protected AdsContext.AdsType getAdsType() {
        return AdsContext.AdsType.ADS_TYPE_VIDEO;
    }
}
