package com.openapi.yewu.ads.mobvista;

import android.app.Activity;

import com.openapi.common.util.LogUtil;
import com.openapi.common.util.ToastUtil;
import com.openapi.ks.free1.R;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.task.ScoreTask;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.RewardInterface;

public class MobvistaRewardManger implements RewardInterface {
//    private MBRewardVideoHandler mMBRewardVideoHandler;
    private final Activity mActivity;
    private final OnAdmobRewardListener mLlistener;
    int index = 0;
    public  static int count=0;
    public MobvistaRewardManger(Activity activity, OnAdmobRewardListener listener){
        this.mActivity = activity;
        this.mLlistener = listener;
//        loadRewardedVideoAd();
//        mMBRewardVideoHandler = new MBRewardVideoHandler(activity, Constants.Mob_REWARD_UNIT, Constants.Mob_REWARD_UNIT_PLACE);
//        mMBRewardVideoHandler.playVideoMute(MBridgeConstans.REWARD_VIDEO_PLAY_MUTE);
//        mMBRewardVideoHandler.setRewardVideoListener(new RewardVideoListener() {
//
//            @Override
//            public void onLoadSuccess(MBridgeIds ids) {
//                LogUtil.i("mobvista reward onLoadSuccess "+ids);
//                if(mMBRewardVideoHandler.isReady())
//                    mMBRewardVideoHandler.show();
//            }
//
//            @Override
//            public void onVideoLoadSuccess(MBridgeIds ids) {
//                LogUtil.i("mobvista reward onVideoLoadSuccess "+ids);
//                if(mMBRewardVideoHandler.isReady())
//                    mMBRewardVideoHandler.show();
//            }
//
//            @Override
//            public void onVideoLoadFail(MBridgeIds ids, String errorMsg) {
//                LogUtil.i("mobvista reward onVideoLoadFail "+errorMsg);
//            }
//
//            @Override
//            public void onShowFail(MBridgeIds ids, String errorMsg) {
//                mLlistener.onNoRewardAD();
//                LogUtil.i("mobvista reward onShowFail "+errorMsg);
//            }
//
//            @Override
//            public void onAdShow(MBridgeIds ids) {
//                LogUtil.i("mobvista reward onAdShow "+ids);
//            }
//
//            @Override
//            public void onAdClose(MBridgeIds ids, RewardInfo rewardInfo) {
//                LogUtil.i("mobvista reward onAdClose "+ids);
//                if(rewardInfo.isCompleteView()) {
//                    //如果rewardInfo.isCompleteView()返回true,代表可以给用户奖励
//                    LogUtil.i("mobvista reward onAdClose  " + rewardInfo.getRewardAmount() + "---" + rewardInfo.getRewardName());
//                    String msg = activity.getResources().getString(R.string.tab_fb_click) + Constants.ADS_REWARD_SHOW_CLICK;
//                    ToastUtil.showShort(msg);
//                    ScoreTask.start(activity, Constants.ADS_REWARD_SHOW_CLICK);
//                    count++;
//                }
//            }
//
//            @Override
//            public void onVideoAdClicked(MBridgeIds ids) {
//                LogUtil.i("mobvista reward onVideoAdClicked "+ids);
//            }
//            @Override
//            public void onVideoComplete(MBridgeIds ids) {
//                LogUtil.i("mobvista reward onVideoComplete "+ids);
//
//            }
//
//            @Override
//            public void onEndcardShow(MBridgeIds ids) {
//                LogUtil.i("mobvista reward onEndcardShow "+ids);
//            }
//
//        });
//
////        loadRewardedVideoAd();
    }
    public void loadRewardedVideoAd() {

    }
    public void onAdResume() {

    }

    public void onAdPause() {

    }

    public void onAdDestroy() {

    }
    public void showAd(){

//        LogUtil.i("mobv showAd "+mMBRewardVideoHandler.isReady());
//        if(count>=3){
//            ToastUtil.showShort(R.string.tab_fb_click_fast);
//            return;
//        }
//        if (mMBRewardVideoHandler.isReady()) {
//            mMBRewardVideoHandler.show();
//        }else{
//            loadRewardedVideoAd();
//            mLlistener.onNoRewardAD();
//            ToastUtil.showShort(R.string.tab_fb_click_no);
//        }
    }
    public boolean next(){
        return false;
    }
}
