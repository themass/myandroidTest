package com.timeline.vpn.ads.launch;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.baidu.mobads.SplashAd;
import com.baidu.mobads.SplashAdListener;
import com.timeline.vpn.constant.Constants;

/**
 * Created by gqli on 2016/3/23.
 */
public class BaiduLaunchController extends LaunchAdsController{
    SplashAdListener listener ;
    public BaiduLaunchController(Activity context, final ViewGroup adsGroup, Handler handler, final ImageButton ibSkip){
        super(context,adsGroup,handler,ibSkip);
        listener = new SplashAdListener() {
            @Override
            public void onAdDismissed() {
                mHandler.sendEmptyMessage(Constants.ADS_DISMISS_MSG);
            }

            @Override
            public void onAdFailed(String arg0) {
                mHandler.sendEmptyMessage(Constants.ADS_NO_MSG);
            }

            @Override
            public void onAdPresent() {
                ibSkip.setVisibility(View.VISIBLE);
                adsGroup.setVisibility(View.VISIBLE);
                mHandler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
            }

            @Override
            public void onAdClick() {
                mHandler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
            }
        };
    }
    @Override
    public void showAds(){
        SplashAd.setAppSid(mContext,Constants.BAIDU_APPID);
        ibSkip.setVisibility(View.GONE);
        adsGroup.setVisibility(View.VISIBLE);
        new SplashAd(mContext, adsGroup, listener, Constants.BAIDU_SPLASHPOSID, true);
    }
}
