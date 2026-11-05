package com.openapi.core.ads.yewu.ads.base;

import android.app.Activity;

import com.openapi.core.ads.yewu.ads.admob.AdmobRewardAds;
public class BaseRewardManger implements RewardInterface {
    public AdmobRewardAds admobRewardManger;
    public RewardInterface base = null;

    public BaseRewardManger(Activity activity, OnAdmobRewardListener listener) {
        admobRewardManger = new AdmobRewardAds(activity, listener);
        base = admobRewardManger;
    }

    public void onAdResume() {

        base.onAdResume();
    }

    public void onAdPause() {

        base.onAdPause();
    }

    public void onAdDestroy() {
        base.onAdDestroy();
    }

    public void showAd() {
        base.showAd();
    }

    public boolean next() {
        return false;
    }

}
