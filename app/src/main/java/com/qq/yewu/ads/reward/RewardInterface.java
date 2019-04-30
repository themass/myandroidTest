package com.qq.yewu.ads.reward;

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
