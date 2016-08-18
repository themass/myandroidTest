package com.timeline.vpn.ads.banner;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;

import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;

import cn.domob.android.ads.AdEventListener;
import cn.domob.android.ads.AdManager;
import cn.domob.android.ads.AdView;

/**
 * Created by gqli on 2016/3/31.
 */
public class DuomBannerController extends BannerAdsController{
    ViewGroup adsView;
    public DuomBannerController(Activity context,Handler handler, ViewGroup adsView){
        super(context, handler);
        this.adsView = adsView;
    }
    @Override
    public void showAds() {
        AdView mAdview = new AdView(mContext, Constants.DM_PUBLISHER_ID, Constants.DM_INLINEPPID);
        mAdview.setKeyword("game");
        mAdview.setUserGender("male");
        mAdview.setUserBirthdayStr("2000-08-08");
        mAdview.setUserPostcode("123456");
        mAdview.setAdEventListener(new AdEventListener() {
            @Override
            public void onEventAdReturned(AdView adView) {
                mHandler.sendEmptyMessage(Constants.ADS_CLOSE_MSG);
            }

            @Override
            public void onAdFailed(AdView adView, AdManager.ErrorCode errorCode) {
                mHandler.sendEmptyMessage(Constants.ADS_NO_MSG);
            }

            @Override
            public void onAdOverlayPresented(AdView adView) {
                adsView.setVisibility(View.VISIBLE);
                adsView.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.anim_splash_enter));
                mHandler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
            }

            @Override
            public void onAdOverlayDismissed(AdView adView) {
                mHandler.sendEmptyMessage(Constants.ADS_DISMISS_MSG);
            }

            @Override
            public void onLeaveApplication(AdView adView) {
                mHandler.sendEmptyMessage(Constants.ADS_CLOSE_MSG);
            }

            @Override
            public void onAdClicked(AdView adView) {
                mHandler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
            }

            @Override
            public Context onAdRequiresCurrentContext() {
                return null;
            }
        });
        RelativeLayout.LayoutParams layout = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        layout.addRule(RelativeLayout.CENTER_HORIZONTAL);
        adsView.addView(mAdview,layout);
    }
}
