package com.timeline.vpn.ads.interstitial;

import android.app.Activity;
import android.os.Handler;

import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import net.youmi.android.spot.SpotDialogListener;
import net.youmi.android.spot.SpotManager;

/**
 * Created by gqli on 2016/3/23.
 */
public class YoumiInterstitialAdsController extends InterstitialAdsController {
    public YoumiInterstitialAdsController(Activity context, Handler handler){
        super(context,handler);
    }

    @Override
    public void showAds() {
        SpotManager.getInstance(mContext).loadSpotAds();
        SpotManager.getInstance(mContext)
                .setSpotOrientation(SpotManager.ORIENTATION_LANDSCAPE);
        SpotManager.getInstance(mContext).setAnimationType(SpotManager.ANIM_ADVANCE);
        SpotManager.getInstance(mContext)
                .showSpotAds(mContext, new SpotDialogListener() {
                    @Override
                    public void onShowSuccess() {
                        mHandler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
                    }

                    @Override
                    public void onShowFailed() {
                        mHandler.sendEmptyMessage(Constants.ADS_NO_MSG);
                    }

                    @Override
                    public void onSpotClosed() {
                        mHandler.sendEmptyMessage(Constants.ADS_CLOSE_MSG);
                    }

                    @Override
                    public void onSpotClick(boolean isWebPath) {
                        mHandler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
                    }
                });
    }
    @Override
    public boolean close(){
        LogUtil.i("close");
        SpotManager.getInstance(mContext).disMiss();
        return true;
    }
}
