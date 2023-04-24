package com.openapi.yewu.ads.admob;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.ToastUtil;
import com.openapi.ks.free1.R;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.task.ScoreTask;
import com.openapi.yewu.ads.base.RewardInterface;

public class AdmobRewardAds implements RewardInterface {
    private RewardedAd mRewardedVideoAd;
    private final Activity mActivity;
    private final RewardInterface.OnAdmobRewardListener mLlistener;
    int index = 0;
    public static int count = 0;

    public AdmobRewardAds(Activity activity, RewardInterface.OnAdmobRewardListener listener) {
        this.mActivity = activity;
        this.mLlistener = listener;
        loadRewardedVideoAd();
    }

    private void loadRewardedVideoAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(mActivity, Constants.ADMOB_REWARD_ID,
                adRequest, new RewardedAdLoadCallback() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        // Handle the error.
                        LogUtil.i(" reward ad loadAdError = " + loadAdError);
                        mRewardedVideoAd = null;
                    }

                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        mRewardedVideoAd = ad;
                        LogUtil.i(" admob reward ad onAdLoaded = " + ad.getAdUnitId());
                        mRewardedVideoAd.setFullScreenContentCallback(new FullScreenContentCallback(){
                            public void onAdClicked() {
                            }

                            public void onAdDismissedFullScreenContent() {
                            }

                            public void onAdFailedToShowFullScreenContent(@NonNull AdError var1) {
                                mLlistener.onNoRewardAD();
                            }

                            public void onAdImpression() {
                            }

                            public void onAdShowedFullScreenContent() {
                            }
                        });
                    }
                });
    }

    public void onAdResume() {
    }

    public void onAdPause() {
    }

    public void onAdDestroy() {
    }

    public void showAd() {

        LogUtil.i("admob reward showAd ");
        if (count >= 3) {
            ToastUtil.showShort(R.string.tab_fb_click_fast);
            return;
        }
        if (mRewardedVideoAd != null) {
            mRewardedVideoAd.show(mActivity, new OnUserEarnedRewardListener() {
                @Override
                public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                    // Handle the reward.
                    LogUtil.i("admob reward onUserEarnedReward  " + rewardItem.getType() + "---" + rewardItem.getAmount());
                    String msg = mActivity.getResources().getString(R.string.tab_fb_click) + Constants.ADS_REWARD_SHOW_CLICK;
                    ToastUtil.showShort(msg);
                    ScoreTask.start(mActivity, Constants.ADS_REWARD_SHOW_CLICK);
                    count++;
                }
            });
        } else {
            loadRewardedVideoAd();
            mLlistener.onNoRewardAD();
            ToastUtil.showShort(R.string.tab_fb_click_no);
        }
    }

    public boolean next() {
        return false;
    }
}
