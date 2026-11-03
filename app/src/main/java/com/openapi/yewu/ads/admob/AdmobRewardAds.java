package com.openapi.yewu.ads.admob;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.ToastUtil;
import com.openapi.ks.free1.R;
import com.openapi.myapp.constant.Constants;
import com.openapi.yewu.ads.base.RewardInterface;

/**
 * 激励视频。
 * <p>
 * 规则（与服务端一致）：
 * <ul>
 *   <li>奖励只由服务端决定：AdMob 的服务端回调（SSV）验证通过后，服务端才发放观看券。
 *       客户端不加分，只负责展示广告，并在获得奖励后通知界面刷新状态。</li>
 *   <li>每个 RewardedAd 只能展示一次：展示结束后立即释放并预加载下一条。</li>
 *   <li>{@code userId} 必须是服务端能识别的账号（用户名），否则 SSV 回调无法归属到用户。</li>
 * </ul>
 */
public class AdmobRewardAds implements RewardInterface {

    /** 获得奖励（服务端稍后才会真正发放）时通知界面。可为 null。 */
    public interface OnRewardEarnedListener {
        void onRewardEarned();
    }

    private RewardedAd mRewardedVideoAd;
    private boolean loading;
    private final Activity mActivity;
    private final RewardInterface.OnAdmobRewardListener mLlistener;
    private final String userId;
    private final OnRewardEarnedListener earnedListener;

    /**
     * 旧调用的两参数版本没有用户，无法做服务端验证，所以不预加载，展示时直接拒绝。
     * 奖励必须使用带 userId 的构造函数。
     */
    public AdmobRewardAds(Activity activity, RewardInterface.OnAdmobRewardListener listener) {
        this(activity, listener, null, null);
    }

    public AdmobRewardAds(Activity activity, RewardInterface.OnAdmobRewardListener listener,
                          String userId, OnRewardEarnedListener earnedListener) {
        this.mActivity = activity;
        this.mLlistener = listener;
        this.userId = userId;
        this.earnedListener = earnedListener;
        if (userId != null) {
            load();
        }
    }

    /** 是否已有可以直接展示的广告。 */
    public boolean isReady() {
        return mRewardedVideoAd != null;
    }

    private void load() {
        if (userId == null || loading || mRewardedVideoAd != null) {
            return;
        }
        loading = true;
        RewardedAd.load(mActivity, Constants.ADMOB_REWARD_ID, new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        loading = false;
                        mRewardedVideoAd = null;
                        LogUtil.i("admob reward load failed: " + loadAdError.getMessage());
                    }

                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        loading = false;
                        mRewardedVideoAd = ad;
                        if (userId != null) {
                            ad.setServerSideVerificationOptions(new ServerSideVerificationOptions.Builder()
                                    .setUserId(userId)
                                    .build());
                        }
                        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                release();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                                release();
                                mLlistener.onNoRewardAD();
                            }
                        });
                    }
                });
    }

    /** 广告用完即释放，并预加载下一条。 */
    private void release() {
        mRewardedVideoAd = null;
        load();
    }

    @Override
    public void onAdResume() {
    }

    @Override
    public void onAdPause() {
    }

    @Override
    public void onAdDestroy() {
        mRewardedVideoAd = null;
    }

    @Override
    public void showAd() {
        if (userId == null) {
            // 没有用户就没有服务端验证，看完也发不了票，直接拒绝
            LogUtil.e("admob reward: userId 为空，拒绝展示激励广告");
            mLlistener.onNoRewardAD();
            return;
        }
        if (mRewardedVideoAd == null) {
            load();
            mLlistener.onNoRewardAD();
            ToastUtil.showShort(R.string.tab_fb_click_no);
            return;
        }
        RewardedAd ad = mRewardedVideoAd;
        ad.show(mActivity, new OnUserEarnedRewardListener() {
            @Override
            public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                // 奖励以服务端 SSV 回调为准；这里只通知界面稍后刷新。
                LogUtil.i("admob reward earned, waiting for server verification");
                if (earnedListener != null) {
                    earnedListener.onRewardEarned();
                }
            }
        });
    }

    @Override
    public boolean next() {
        return false;
    }
}
