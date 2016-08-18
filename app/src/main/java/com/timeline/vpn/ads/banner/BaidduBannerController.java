package com.timeline.vpn.ads.banner;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;

import com.baidu.mobads.AdView;
import com.baidu.mobads.AdViewListener;
import com.timeline.vpn.R;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import org.json.JSONObject;


/**
 * Created by gqli on 2016/3/31.
 */
public class BaidduBannerController extends BannerAdsController{
    ViewGroup adsView;
    public BaidduBannerController(Activity context, Handler handler, ViewGroup adsView){
        super(context, handler);
        this.adsView = adsView;
    }
    @Override
    public void showAds() {
        AdView.setAppSid(mContext, Constants.BAIDU_APPID);
        AdView adView = new AdView(mContext, Constants.BAIDU_BANNERID);
        adView.setListener(new AdViewListener() {
            public void onAdSwitch() {
                LogUtil.i("onAdSwitch");
            }

            public void onAdShow(JSONObject info) {
                adsView.setVisibility(View.VISIBLE);
                adsView.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.anim_splash_enter));
                mHandler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
            }

            public void onAdReady(AdView adView) {
                mHandler.sendEmptyMessage(Constants.ADS_READY_MSG);
            }

            public void onAdFailed(String reason) {
                mHandler.sendEmptyMessage(Constants.ADS_NO_MSG);
            }

            public void onAdClick(JSONObject info) {
                mHandler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
            }
        });
        RelativeLayout.LayoutParams rllp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        rllp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        adsView.addView(adView,rllp);
    }
}
