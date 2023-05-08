package com.openapi.yewu.ads.mobvista;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;

import com.openapi.myapp.constant.Constants;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.InterstitialAdsInter;

/**
 * Created by dengt on 2017/9/20.
 */

public class MobvistaInterstitialAds extends InterstitialAdsInter {
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_INTERSTITIAL;
    }
    @Override
    public void interstitialAds(final Context context, final Handler handler,String key,boolean score,final int count){
//        MBNewInterstitialHandler  mMBInterstitalVideoHandler = new MBNewInterstitialHandler(context, Constants.Mob_INTER_UNIT, Constants.Mob_INTER_UNIT_PLACE);
//        mMBInterstitalVideoHandler.playVideoMute(MBridgeConstans.REWARD_VIDEO_PLAY_MUTE);
//        mMBInterstitalVideoHandler.setInterstitialVideoListener(new NewInterstitialListener() {
//
//            @Override
//            public void onLoadCampaignSuccess(MBridgeIds ids) {
//                LogUtil.i( " MobvistaInterstitialAds onLoadCampaignSuccess: " + Thread.currentThread() + " " + ids.toString());
//                if(mMBInterstitalVideoHandler.isReady())
//                    mMBInterstitalVideoHandler.show();
//            }
//
//            @Override
//            public void onResourceLoadSuccess(MBridgeIds ids) {
//                LogUtil.i( " MobvistaInterstitialAds onResourceLoadSuccess: " + Thread.currentThread() + " " + ids.toString());
//                if(mMBInterstitalVideoHandler.isReady())
//                    mMBInterstitalVideoHandler.show();
//            }
//
//            @Override
//            public void onResourceLoadFail(MBridgeIds ids, String errorMsg) {
//                LogUtil.i( " MobvistaInterstitialAds onResourceLoadFail errorMsg: " + errorMsg + " " + ids.toString());
//                noAds(context,handler, AdsContext.AdsFrom.MOBVISTA,count);
//            }
//
//            @Override
//            public void onShowFail(MBridgeIds ids, String errorMsg) {
//                LogUtil.i( " MobvistaInterstitialAds onShowFail: " + errorMsg + " " + ids.toString());
//                noAds(context,handler, AdsContext.AdsFrom.MOBVISTA,count);
//            }
//
//            @Override
//            public void onAdShow(MBridgeIds ids) {
//                LogUtil.i( " MobvistaInterstitialAds onAdShow: "+ ids.toString());
//                displayAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
//            }
//
//            @Override
//            public void onAdClose(MBridgeIds ids, RewardInfo info) {
//                LogUtil.i( " MobvistaInterstitialAds onAdClose: " +  "isCompleteView：" + info.isCompleteView() + " " + ids.toString());
//                closeAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
//            }
//
//            @Override
//            public void onAdClicked(MBridgeIds ids) {
//                LogUtil.i( " MobvistaInterstitialAds onAdClicked: " + ids.toString());
//                clickAds(context, handler, AdsContext.AdsFrom.MOBVISTA);
//            }
//
//            @Override
//            public void onVideoComplete(MBridgeIds ids) {
//                LogUtil.i( " MobvistaInterstitialAds onVideoComplete: " + ids.toString());
//            }
//            @Override
//            public void onAdCloseWithNIReward(MBridgeIds ids, RewardInfo info) {
//                LogUtil.i( " MobvistaInterstitialAds onAdCloseWithNIReward: " + ids.toString() + "  " + info.toString());
//            }
//
//            @Override
//            public void onEndcardShow(MBridgeIds ids) {
//                LogUtil.i( " MobvistaInterstitialAds onEndcardShow: " + ids.toString());
//            }
//
//        });
//        mMBInterstitalVideoHandler.load();
//
//        try {
//        } catch (Throwable e) {
//            noAds(context,handler, AdsContext.AdsFrom.MOBVISTA,count);
//            LogUtil.e(e);
//        }
    }

    @Override
    public void interstitialExit(Context context,String key){

    }
}
