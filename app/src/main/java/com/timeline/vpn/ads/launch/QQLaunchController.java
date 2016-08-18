package com.timeline.vpn.ads.launch;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.qq.e.ads.splash.SplashAD;
import com.qq.e.ads.splash.SplashADListener;
import com.timeline.vpn.constant.Constants;

/**
 * Created by gqli on 2016/3/23.
 */
public class QQLaunchController extends LaunchAdsController{
    public QQLaunchController(Activity context,ViewGroup adsGroup,Handler handler,ImageButton ibSkip){
        super(context,adsGroup,handler,ibSkip);
    }
    @Override
    public void showAds(){
        ibSkip.setVisibility(View.GONE);
        SplashAD ad = new SplashAD(mContext, adsGroup, Constants.QQ_APPID, Constants.QQ_SPLASHPOSID,
                new SplashADListener() {
                    @Override
                    public void onNoAD(int i) {
                        mHandler.sendEmptyMessage(Constants.ADS_NO_MSG);
                    }
                    @Override
                    public void onADClicked() {
                        mHandler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
                    }
                    public void onADPresent() {
                        mHandler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
                    }
                    //广告展示时间结束（5s）或者用户点击关闭时调用。
                    public void onADDismissed() {
                        mHandler.sendEmptyMessage(Constants.ADS_DISMISS_MSG);
                    }
                });

    }
}
