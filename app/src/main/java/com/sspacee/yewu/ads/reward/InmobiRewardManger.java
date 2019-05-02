package com.sspacee.yewu.ads.reward;
import android.app.Activity;

import com.inmobi.ads.InMobiAdRequestStatus;
import com.inmobi.ads.InMobiInterstitial;
import com.inmobi.ads.listeners.InterstitialAdEventListener;
import com.qq.sexfree.R;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.ToastUtil;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.task.ScoreTask;

import java.util.Map;

public class InmobiRewardManger implements RewardInterface{
    private InMobiInterstitial interstitialAd ;
    private final Activity mActivity;
    private final OnAdmobRewardListener mLlistener;
    int index = 0;
    public  static int count=0;
    public InmobiRewardManger(Activity activity, OnAdmobRewardListener listener){
        this.mActivity = activity;
        this.mLlistener = listener;
        interstitialAd = new InMobiInterstitial(activity, Constants.INMOBI_APPKEY,new MyListener());
        loadRewardedVideoAd();
    }
    public  class MyListener extends InterstitialAdEventListener {
        public void onAdLoadSucceeded(InMobiInterstitial var1) {
            LogUtil.i(" inmobionAdLoadSucceeded-"+var1);
        }

        public void onAdLoadFailed(InMobiInterstitial var1, InMobiAdRequestStatus var2) {
            LogUtil.i(" inmobionAdLoadFailed-"+var1+"--"+var2);
        }

        public void onAdReceived(InMobiInterstitial var1) {
            LogUtil.i(" inmobionAdReceived-");
        }

        public void onAdClicked(InMobiInterstitial var1, Map<Object, Object> var2) {
            LogUtil.i(" inmobionAdClicked-"+var1+"--"+var2);

        }
        public void onAdDisplayed(InMobiInterstitial var1) {
            LogUtil.i(" inmobionAdDisplayed-"+var1);
        }

        public void onRewardsUnlocked(InMobiInterstitial var1, Map<Object, Object> var2) {
            LogUtil.i(" inmobionRewardsUnlocked-"+var1+"---"+var2);
            String msg = mActivity.getResources().getString(R.string.tab_fb_click) + Constants.ADS_REWARD_SHOW_CLICK;
            ToastUtil.showShort(msg);
            ScoreTask.start(mActivity, Constants.ADS_REWARD_SHOW_CLICK);
            count++;
        }

        public void onRequestPayloadCreated(byte[] var1) {
            LogUtil.i(" inmobionRequestPayloadCreated-"+var1);
        }

        public void onRequestPayloadCreationFailed(InMobiAdRequestStatus var1) {
            LogUtil.i(" inmobionRequestPayloadCreationFailed-"+var1);
        }

        @Override
        public void onAdWillDisplay(InMobiInterstitial inMobiInterstitial) {
            LogUtil.i(" inmobionAdWillDisplay-"+inMobiInterstitial);
        }

        @Override
        public void onAdDisplayFailed(InMobiInterstitial inMobiInterstitial) {
            LogUtil.i(" inmobionAdDisplayFailed-"+inMobiInterstitial);        }

        @Override
        public void onAdDismissed(InMobiInterstitial inMobiInterstitial) {
            LogUtil.i(" inmobionAdDismissed-"+inMobiInterstitial);
        }
    }
    public void loadRewardedVideoAd() {
        interstitialAd.load();
    }
    public void onAdResume() {

    }

    public void onAdPause() {

    }

    public void onAdDestroy() {

    }
    public void showAd(){

        LogUtil.i(" inmobishowAd "+interstitialAd.isReady());
        if(count>=3){
            ToastUtil.showShort(R.string.tab_fb_click_fast);
            return;
        }
        if (interstitialAd.isReady()) {
            interstitialAd.show();
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
