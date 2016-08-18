package com.timeline.vpn.ads.launch;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.timeline.vpn.constant.Constants;

import cn.domob.android.ads.RTSplashAd;
import cn.domob.android.ads.RTSplashAdListener;
import cn.domob.android.ads.SplashAd;

/**
 * Created by gqli on 2016/3/23.
 */
public class DuomLaunchController extends LaunchAdsController{
    RTSplashAd splashAd;
    public DuomLaunchController(Activity context, final ViewGroup adsGroup, Handler handler, final ImageButton ibSkip){
        super(context, adsGroup, handler, ibSkip);
        splashAd = new RTSplashAd(mContext, Constants.DM_PUBLISHER_ID, Constants.DM_SPLASHPPID,
                SplashAd.SplashMode.SplashModeBigEmbed);
        splashAd.setRTSplashAdListener(new RTSplashAdListener() {
            @Override
            public void onRTSplashDismiss() {
                mHandler.sendEmptyMessage(Constants.ADS_DISMISS_MSG);
            }

            @Override
            public void onRTSplashLoadFailed() {
                mHandler.sendEmptyMessage(Constants.ADS_NO_MSG);
            }

            @Override
            public void onRTSplashPresent() {
                ibSkip.setVisibility(View.VISIBLE);
                mHandler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
            }
        });
    }
    @Override
    public void showAds(){
        splashAd.splash(mContext,adsGroup);
    }
}
