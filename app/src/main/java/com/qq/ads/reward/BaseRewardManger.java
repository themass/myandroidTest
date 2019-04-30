package com.qq.ads.reward;

import android.app.Activity;

public class BaseRewardManger implements RewardInterface {
    public RewardInterface admobRewardManger;
    public MobvistaRewardManger mobvistaRewardManger;
    private RewardInterface inmobiRewardManger;
    public RewardInterface base=null;
    public BaseRewardManger(Activity activity, RewardInterface.OnAdmobRewardListener listener){
        admobRewardManger = new AdmobRewardManger(activity,listener);
        mobvistaRewardManger = new MobvistaRewardManger(activity,listener);
        inmobiRewardManger = new InmobiRewardManger(activity,listener);
        base = admobRewardManger;
    }
    public void onAdResume(){
        base.onAdResume();
    }

    public void onAdPause(){
        base.onAdPause();
    }

    public void onAdDestroy(){
        base.onAdDestroy();
    }
    public void showAd(){
        base.showAd();
    }
    public boolean next(){
        if(base == admobRewardManger){
            base=mobvistaRewardManger;
            base.showAd();
            return true;
        }else if(base == mobvistaRewardManger){
            base=inmobiRewardManger;
            base.showAd();
            return true;
        }
        base = admobRewardManger;
        return false;
    }

}
