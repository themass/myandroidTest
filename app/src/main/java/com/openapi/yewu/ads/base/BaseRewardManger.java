package com.openapi.yewu.ads.base;

import android.app.Activity;

import com.openapi.yewu.ads.admob.AdmobRewardAds;
import com.openapi.yewu.ads.mobvista.MobvistaRewardManger;

public class BaseRewardManger implements RewardInterface {
    public AdmobRewardAds admobRewardManger;
    public MobvistaRewardManger mobvistaRewardManger;
    public RewardInterface base=null;
    public BaseRewardManger(Activity activity, RewardInterface.OnAdmobRewardListener listener){
        admobRewardManger = new AdmobRewardAds(activity,listener);
        mobvistaRewardManger = new MobvistaRewardManger(activity,listener);
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
    public void showAd()
    {
        base.showAd();
    }
    public boolean next(){
        if(base == admobRewardManger){
//            base=mobvistaRewardManger;
            base.showAd();
            return true;
        }
        base = admobRewardManger;
        return false;
    }

}
