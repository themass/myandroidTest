package com.qq.yewu.ads.mobvista;

import android.content.Context;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.mintegral.msdk.MIntegralConstans;
import com.mintegral.msdk.MIntegralSDK;
import com.mintegral.msdk.nativex.view.mtgfullview.StarLevelLayoutView;
import com.mintegral.msdk.out.Campaign;
import com.mintegral.msdk.out.Frame;
import com.mintegral.msdk.out.MIntegralSDKFactory;
import com.mintegral.msdk.out.MtgNativeHandler;
import com.mintegral.msdk.out.NativeListener;
import com.qq.common.util.LogUtil;
import com.qq.common.util.PreferenceUtils;
import com.qq.fq2.R;
import com.qq.myapp.base.MyApplication;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.ImagePhotoLoad;
import com.qq.yewu.ads.base.AdsContext;
import com.qq.yewu.ads.base.SplashAdsInter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by dengt on 2017/9/20.
 */

public class SplashMobvAds extends SplashAdsInter {
    private MtgNativeHandler nativeHandle;
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_SPREAD;
    }
    @Override
    public  void lanchExit(Context context,RelativeLayout group){
    }
    protected void fillFullScreenLayout(Context context,RelativeLayout group, List<Campaign> campaigns) {
        if (campaigns != null && campaigns.size() > 0) {
            Campaign campaign = campaigns.get(0);
            View view = LayoutInflater.from(context).inflate(R.layout.mobv_splash_view,null);
            ImageView mIvIcon = (ImageView) view.findViewById(R.id.mintegral_full_screen_iv_icon);
            ImageView mIvImage = (ImageView)  view.findViewById(R.id.mintegral_full_screen_iv_image);
            TextView mTvAppName = (TextView)  view.findViewById(R.id.mintegral_full_screen_iv_app_name);
            TextView mTvAppDesc = (TextView)  view.findViewById(R.id.mintegral_full_screen_tv_app_desc);
            TextView mTvCta = (TextView)  view.findViewById(R.id.mintegral_full_screen_tv_cta);
            StarLevelLayoutView mStarLayout = (StarLevelLayoutView)  view.findViewById(R.id.mintegral_full_screen_star);
            LinearLayout mLl_Root = (LinearLayout)  view.findViewById(R.id.mintegral_full_screen_ll_root);
            if (!TextUtils.isEmpty(campaign.getIconUrl())) {
                ImagePhotoLoad.loadCommonImg(context,campaign.getIconUrl(),mIvIcon);
            }
            if (!TextUtils.isEmpty(campaign.getImageUrl())) {
                ImagePhotoLoad.loadCommonImg(context,campaign.getImageUrl(),mIvImage);
            }
            mTvAppName.setText(campaign.getAppName() + "");
            mTvAppDesc.setText(campaign.getAppDesc() + "");
            mTvCta.setText(campaign.getAdCall());
            int rating = (int) campaign.getRating();
            mStarLayout.setRating(rating);
            nativeHandle.registerView(mTvCta, campaign);
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) {
                parent.removeAllViews();
            }
            group.addView(view);
        }
    }

    public void preloadNative() {

        MIntegralSDK sdk = MIntegralSDKFactory.getMIntegralSDK();
        Map<String, Object> preloadMap = new HashMap<String, Object>();
        preloadMap.put(MIntegralConstans.PROPERTIES_LAYOUT_TYPE, MIntegralConstans.LAYOUT_NATIVE);
        preloadMap.put(MIntegralConstans.PROPERTIES_UNIT_ID, Constants.Mob_UNIT_SPLASH);

        List<NativeListener.Template> list = new ArrayList<NativeListener.Template>();
        list.add(new NativeListener.Template(MIntegralConstans.TEMPLATE_BIG_IMG, 1));
        preloadMap.put(MIntegralConstans.NATIVE_INFO, MtgNativeHandler.getTemplateString(list));
        sdk.preload(preloadMap);

    }
    @Override
    public  void launchAds(final FragmentActivity context, final RelativeLayout group, RelativeLayout skipView, final Handler handler){
        try {
            String key = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.Mob_UNIT_SPLASH_KAY, Constants.Mob_UNIT_SPLASH);
            Map<String, Object> properties = MtgNativeHandler.getNativeProperties(key);
            nativeHandle = new MtgNativeHandler(properties, context);
            nativeHandle.addTemplate(new NativeListener.Template(MIntegralConstans.TEMPLATE_BIG_IMG, 1));
            nativeHandle.setAdListener(new NativeListener.NativeAdListener() {
                @Override
                public void onAdLoaded(List<Campaign> campaigns, int template) {
                    fillFullScreenLayout(context,group,campaigns);
                    preloadNative();
                }

                @Override
                public void onAdLoadError(String message) {
                    LogUtil.e("mobv onAdLoadError:" + message);
                    noAds(context,handler, AdsContext.AdsFrom.MOBVISTA,0);
                }

                @Override
                public void onAdFramesLoaded(List<Frame> list) {

                }

                @Override
                public void onLoggingImpression(int adsourceType) {

                }

                @Override
                public void onAdClick(Campaign campaign) {
                    clickAds(context, handler, AdsContext.AdsFrom.MOBVISTA);
                }
            });
            nativeHandle.load();
        } catch (Throwable e) {
            noAds(context,handler, AdsContext.AdsFrom.MOBVISTA,0);
            LogUtil.e(e);
        }
    }
}
