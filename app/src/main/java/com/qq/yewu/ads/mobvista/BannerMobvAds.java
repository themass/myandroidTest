package com.qq.yewu.ads.mobvista;

import android.content.Context;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.mintegral.msdk.MIntegralConstans;
import com.mintegral.msdk.MIntegralSDK;
import com.mintegral.msdk.out.Campaign;
import com.mintegral.msdk.out.Frame;
import com.mintegral.msdk.out.MIntegralSDKFactory;
import com.mintegral.msdk.out.MtgNativeHandler;
import com.mintegral.msdk.out.NativeListener;
import com.qq.common.util.LogUtil;
import com.qq.ks.free1.R;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.ImagePhotoLoad;
import com.qq.yewu.ads.base.AdsContext;
import com.qq.yewu.ads.base.BannerInter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by dengt on 2017/9/20.
 */

public class BannerMobvAds extends BannerInter {
    private MtgNativeHandler nativeHandle;
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_BANNER;
    }

    protected void fillBannerLayout(Context context,ViewGroup group,List<Campaign> campaigns) {
        View view = LayoutInflater.from(context).inflate(R.layout.mobv_banner_view,null);
        RelativeLayout mRl_Root = (RelativeLayout) view.findViewById(R.id.mintegral_banner_rl_root);
        ImageView mIvIcon = (ImageView) view.findViewById(R.id.mintegral_banner_iv_icon);
        TextView mTvAppName = (TextView) view.findViewById(R.id.mintegral_banner_tv_title);
        TextView mTvAppDesc = (TextView) view.findViewById(R.id.mintegral_banner_tv_app_desc);
        TextView mTvCta = (TextView) view.findViewById(R.id.mintegral_banner_tv_cta);
        if (campaigns != null && campaigns.size() > 0) {
            Campaign campaign = campaigns.get(0);
            if (!TextUtils.isEmpty(campaign.getIconUrl())) {
                ImagePhotoLoad.loadCommonImg(context,campaign.getIconUrl(),mIvIcon);
            }

            mTvAppName.setText(campaign.getAppName() + "");
            mTvAppDesc.setText(campaign.getAppDesc() + "");
            mTvCta.setText(campaign.getAdCall());
            nativeHandle.registerView(mRl_Root, campaign);
        }
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) {
            parent.removeAllViews();
        }
        group.addView(view);
    }
    public void preloadNative() {
        MIntegralSDK sdk = MIntegralSDKFactory.getMIntegralSDK();
        Map<String, Object> preloadMap = new HashMap<String, Object>();
        preloadMap.put(MIntegralConstans.PROPERTIES_LAYOUT_TYPE, MIntegralConstans.LAYOUT_NATIVE);
        preloadMap.put(MIntegralConstans.PROPERTIES_UNIT_ID, Constants.Mob_UNIT_BANNER1);
        List<NativeListener.Template> list = new ArrayList<NativeListener.Template>();
        list.add(new NativeListener.Template(MIntegralConstans.TEMPLATE_MULTIPLE_IMG, 1));
        preloadMap.put(MIntegralConstans.NATIVE_INFO, MtgNativeHandler.getTemplateString(list));
        sdk.preload(preloadMap);

    }
    @Override
    public void bannerAds(final FragmentActivity context, final ViewGroup group, final String key, final Handler handler){
        try{

            Map<String, Object> properties = MtgNativeHandler.getNativeProperties(Constants.adviewToMobvBanner.get(key));
            nativeHandle = new MtgNativeHandler(properties, context);
            nativeHandle.addTemplate(new NativeListener.Template(MIntegralConstans.TEMPLATE_MULTIPLE_IMG, 1));
            nativeHandle.setAdListener(new NativeListener.NativeAdListener() {

                @Override
                public void onAdLoaded(List<Campaign> campaigns, int template) {
                    readyAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
                    fillBannerLayout(context,group,campaigns);
                    preloadNative();
                }

                @Override
                public void onAdLoadError(String message) {
                    LogUtil.e("mobv onAdLoadError:" + message);
                    group.setVisibility(View.GONE);
                    noAds(context,handler, AdsContext.AdsFrom.MOBVISTA,0);
                }

                @Override
                public void onAdFramesLoaded(List<Frame> list) {
                    displayAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
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
    @Override
    public void bannerExit(FragmentActivity context, ViewGroup group, final String key){
        LogUtil.i("bannerExit:"+key);
        group.removeView(group.findViewWithTag(key));
    }


}
