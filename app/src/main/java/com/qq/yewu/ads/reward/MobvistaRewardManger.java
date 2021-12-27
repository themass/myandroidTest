package com.qq.yewu.ads.reward;

import android.app.Activity;

import com.mintegral.msdk.out.MTGRewardVideoHandler;
import com.mintegral.msdk.out.RewardVideoListener;
import com.qq.common.util.LogUtil;
import com.qq.common.util.ToastUtil;
import com.qq.ks1.R;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.task.ScoreTask;

public class MobvistaRewardManger implements RewardInterface{
    private MTGRewardVideoHandler mMTGRewardVideoHandler;
    private final Activity mActivity;
    private final OnAdmobRewardListener mLlistener;
    int index = 0;
    public  static int count=0;
    public MobvistaRewardManger(Activity activity, OnAdmobRewardListener listener){
        this.mActivity = activity;
        this.mLlistener = listener;
//        CommonUtil commonUtil=new CommonUtil(activity.getApplicationContext());
//        commonUtil.checkRewardVideo();

        mMTGRewardVideoHandler = new MTGRewardVideoHandler(activity, Constants.Mob_UNIT_REWARD);
        mMTGRewardVideoHandler.setRewardVideoListener(new RewardVideoListener() {

            @Override
            public void onLoadSuccess(String unitId) {
                LogUtil.i("mobv onLoadSuccess"+unitId);
            }

            @Override
            public void onVideoLoadSuccess(String unitId) {
                LogUtil.i("mobv onVideoLoadSuccess"+unitId);
            }

            @Override
            public void onVideoLoadFail(String errorMsg) {
                LogUtil.i("mobv onVideoLoadFail"+errorMsg);
            }

            @Override
            public void onShowFail(String errorMsg) {
                LogUtil.i("mobv onShowFail"+errorMsg);
            }

            @Override
            public void onAdShow() {
                LogUtil.i("mobv onAdShow");
            }

            @Override
            public void onAdClose(boolean isCompleteView, String RewardName, float RewardAmout) {
                LogUtil.i("mobv onAdClose-"+isCompleteView+"-"+RewardName+"-"+RewardAmout);
            }

            @Override
            public void onVideoAdClicked(String unitId) {
                LogUtil.i("mobv onVideoAdClicked-"+unitId);

            }
            @Override
            public void onVideoComplete(String unitId) {
                LogUtil.i( "onVideoComplete---"+unitId);
                String msg = mActivity.getResources().getString(R.string.tab_fb_click) + Constants.ADS_REWARD_SHOW_CLICK;
                ToastUtil.showShort(msg);
                ScoreTask.start(mActivity, Constants.ADS_REWARD_SHOW_CLICK);
                count++;
            }

            @Override
            public void onEndcardShow(String unitId) {
                LogUtil.i( "onEndcardShow---"+unitId);
            }

        });
        loadRewardedVideoAd();
    }
    public void loadRewardedVideoAd() {
        mMTGRewardVideoHandler.load();
    }
    public void onAdResume() {

    }

    public void onAdPause() {

    }

    public void onAdDestroy() {

    }
    public void showAd(){

        LogUtil.i("mobv showAd "+mMTGRewardVideoHandler.isReady());
        if(count>=3){
            ToastUtil.showShort(R.string.tab_fb_click_fast);
            return;
        }
        if (mMTGRewardVideoHandler.isReady()) {
            mMTGRewardVideoHandler.show("111", "themass");
        }else{
            loadRewardedVideoAd();
            mLlistener.onNoRewardAD();
            ToastUtil.showShort(R.string.tab_fb_click_no);
        }
    }
    public boolean next(){
        return false;
    }
}
