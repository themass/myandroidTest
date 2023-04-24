package com.openapi.yewu.ads.base;

public interface RewardInterface {
    public void onAdResume();
    public void onAdPause();
    public void onAdDestroy();
    public void showAd();
    public boolean next();
    public interface OnAdmobRewardListener{
        public void onNoRewardAD();
    }
}
