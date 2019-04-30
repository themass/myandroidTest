package com.qq.yewu.ads.reward;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAd;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;
import com.qq.common.util.LogUtil;
import com.qq.common.util.ToastUtil;
import com.qq.e.ads.interstitial.AbstractInterstitialADListener;
import com.qq.e.ads.interstitial.InterstitialAD;
import com.qq.e.comm.util.AdError;
import com.qq.e.comm.util.GDTLogger;
import com.qq.fq2.R;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.task.ScoreTask;

public class AdmobRewardManger implements RewardInterface{
    private RewardedVideoAd mRewardedVideoAd;
    private final Activity mActivity;
    private final RewardInterface.OnAdmobRewardListener mLlistener;
    int index = 0;
    public  static int count=0;
    public AdmobRewardManger(Activity activity, RewardInterface.OnAdmobRewardListener listener){
        this.mActivity = activity;
        this.mLlistener = listener;
        MobileAds.initialize(activity, Constants.ADMOB_REWARD_ID);
        mRewardedVideoAd = MobileAds.getRewardedVideoAdInstance(activity);
        mRewardedVideoAd.setRewardedVideoAdListener(new RewardedVideoAdListener(){
            @Override
            public void onRewarded(RewardItem reward) {
                LogUtil.i( reward.getType()+"---"+reward.getAmount());
                String msg = mActivity.getResources().getString(R.string.tab_fb_click) + Constants.ADS_REWARD_SHOW_CLICK;
                ToastUtil.showShort(msg);
                ScoreTask.start(mActivity, Constants.ADS_REWARD_SHOW_CLICK);
                count++;
            }

            @Override
            public void onRewardedVideoAdLeftApplication() {
                LogUtil.i("onRewardedVideoAdLeftApplication");
            }

            @Override
            public void onRewardedVideoAdClosed() {
                LogUtil.i( "onRewardedVideoAdClosed");
                loadRewardedVideoAd();
            }

            @Override
            public void onRewardedVideoAdFailedToLoad(int errorCode) {
                LogUtil.i( "onRewardedVideoAdFailedToLoad--"+errorCode);
            }

            @Override
            public void onRewardedVideoAdLoaded() {
                LogUtil.i( "onRewardedVideoAdLoaded");
            }

            @Override
            public void onRewardedVideoAdOpened() {
                LogUtil.i( "onRewardedVideoAdOpened");
            }

            @Override
            public void onRewardedVideoStarted() {
                LogUtil.i( "onRewardedVideoStarted");
            }

            @Override
            public void onRewardedVideoCompleted() {
                LogUtil.i( "onRewardedVideoCompleted");
            }
        });
        loadRewardedVideoAd();
    }
    private void loadRewardedVideoAd() {
        mRewardedVideoAd.loadAd(Constants.ADMOB_REWARD_UNIT_IDS.get((index++)%Constants.ADMOB_REWARD_UNIT_IDS.size()),
                new AdRequest.Builder().build());
    }
    public void onAdResume() {
        mRewardedVideoAd.resume(mActivity);
    }

    public void onAdPause() {
        mRewardedVideoAd.pause(mActivity);
    }

    public void onAdDestroy() {
        mRewardedVideoAd.destroy(mActivity);
    }
    public void showAd(){

        LogUtil.i("showAd "+mRewardedVideoAd.isLoaded());
        if(count>=3){
            ToastUtil.showShort(R.string.tab_fb_click_fast);
            return;
        }
        if (mRewardedVideoAd.isLoaded()) {
            mRewardedVideoAd.show();
        }else{
            loadRewardedVideoAd();
            mLlistener.onNoRewardAD();
            ToastUtil.showShort(R.string.tab_fb_click_no);
        }
    }
    public boolean next(){
        return true;
    }
}
