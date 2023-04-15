package com.openapi.yewu.ads.mobvista;

import android.os.Handler;
import androidx.fragment.app.FragmentActivity;

import android.view.ViewGroup;

import com.openapi.common.util.LogUtil;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.BannerInter;

/**
 * Created by dengt on 2017/9/20.
 */

public class BannerMobvAds extends BannerInter {
//    private MtgNativeHandler nativeHandle;
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_BANNER;
    }

    public void preloadNative() {
//        MIntegralSDK sdk = MIntegralSDKFactory.getMIntegralSDK();
//        Map<String, Object> preloadMap = new HashMap<String, Object>();
//        preloadMap.put(MIntegralConstans.PROPERTIES_LAYOUT_TYPE, MIntegralConstans.LAYOUT_NATIVE);
//        preloadMap.put(MIntegralConstans.PROPERTIES_UNIT_ID, Constants.Mob_UNIT_BANNER1);
//        List<NativeListener.Template> list = new ArrayList<NativeListener.Template>();
//        list.add(new NativeListener.Template(MIntegralConstans.TEMPLATE_MULTIPLE_IMG, 1));
//        preloadMap.put(MIntegralConstans.NATIVE_INFO, MtgNativeHandler.getTemplateString(list));
//        sdk.preload(preloadMap);

    }
    @Override
    public void bannerAds(final FragmentActivity context, final ViewGroup group, final String key, final Handler handler){
//        try{
//
//            Map<String, Object> properties = MtgNativeHandler.getNativeProperties(Constants.adviewToMobvBanner.get(key));
//            nativeHandle = new MtgNativeHandler(properties, context);
//            nativeHandle.addTemplate(new NativeListener.Template(MIntegralConstans.TEMPLATE_MULTIPLE_IMG, 1));
//            nativeHandle.setAdListener(new NativeListener.NativeAdListener() {
//
//                @Override
//                public void onAdLoaded(List<Campaign> campaigns, int template) {
//                    readyAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
//                    fillBannerLayout(context,group,campaigns);
//                    preloadNative();
//                }
//
//                @Override
//                public void onAdLoadError(String message) {
//                    LogUtil.e("mobv onAdLoadError:" + message);
//                    group.setVisibility(View.GONE);
//                    noAds(context,handler, AdsContext.AdsFrom.MOBVISTA,0);
//                }
//
//                @Override
//                public void onAdFramesLoaded(List<Frame> list) {
//                    displayAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
//                }
//
//                @Override
//                public void onLoggingImpression(int adsourceType) {
//
//                }
//
//                @Override
//                public void onAdClick(Campaign campaign) {
//                    clickAds(context, handler, AdsContext.AdsFrom.MOBVISTA);
//                }
//            });
//            nativeHandle.load();
//        } catch (Throwable e) {
//            noAds(context,handler, AdsContext.AdsFrom.MOBVISTA,0);
//            LogUtil.e(e);
//         }
    }
    @Override
    public void bannerExit(FragmentActivity context, ViewGroup group, final String key){
        LogUtil.i("bannerExit:"+key);
        group.removeView(group.findViewWithTag(key));
    }


}
