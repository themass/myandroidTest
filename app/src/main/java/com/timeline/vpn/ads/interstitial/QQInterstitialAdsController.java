package com.timeline.vpn.ads.interstitial;

import android.app.Activity;
import android.os.Handler;

import com.qq.e.ads.interstitial.AbstractInterstitialADListener;
import com.qq.e.ads.interstitial.InterstitialAD;
import com.timeline.vpn.constant.Constants;

/**
 * Created by gqli on 2016/3/23.
 */
public class QQInterstitialAdsController extends InterstitialAdsController {
    InterstitialAD  iad;
    public QQInterstitialAdsController(Activity context, Handler handler){
        super(context,handler);
        iad = new InterstitialAD(mContext, Constants.QQ_APPID, Constants.QQ_INTERTERISTALPOSID);
        iad.setADListener(new AbstractInterstitialADListener() {
            @Override
            public void onNoAD(int arg0) {
                mHandler.sendEmptyMessage(Constants.ADS_NO_MSG);
            }
            @Override
            public void onADReceive() {
                mHandler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
                iad.show();
            }
        });
    }

    @Override
    public void showAds() {

        iad.loadAD();
    }
    @Override
    public boolean close(){
        iad.destory();
        return true;
    }
}
