package com.openapi.yewu.ads.mobvista;

import android.content.Context;
import android.os.Handler;
import androidx.fragment.app.FragmentActivity;

import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.openapi.common.util.EventBusUtil;
import com.openapi.common.util.LogUtil;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.config.SplashAdDissmisEvent;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.SplashAdsInter;

/**
 * Created by dengt on 2017/9/20.
 */

public class MobvistaSplashAds extends SplashAdsInter {
//    private MBSplashHandler mbSplashHandler;
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_SPREAD;
    }
    @Override
    public  void lanchExit(Context context,RelativeLayout group){

//        mbSplashHandler.onDestroy();
    }
//    protected void fillFullScreenLayout(Context context,RelativeLayout group, List<Campaign> campaigns) {
////        if (campaigns != null && campaigns.size() > 0) {
////            Campaign campaign = campaigns.get(0);
////            View view = LayoutInflater.from(context).inflate(R.layout.mobv_splash_view,null);
////            ImageView mIvIcon = (ImageView) view.findViewById(R.id.mintegral_full_screen_iv_icon);
////            ImageView mIvImage = (ImageView)  view.findViewById(R.id.mintegral_full_screen_iv_image);
////            TextView mTvAppName = (TextView)  view.findViewById(R.id.mintegral_full_screen_iv_app_name);
////            TextView mTvAppDesc = (TextView)  view.findViewById(R.id.mintegral_full_screen_tv_app_desc);
////            TextView mTvCta = (TextView)  view.findViewById(R.id.mintegral_full_screen_tv_cta);
////            StarLevelLayoutView mStarLayout = (StarLevelLayoutView)  view.findViewById(R.id.mintegral_full_screen_star);
////            LinearLayout mLl_Root = (LinearLayout)  view.findViewById(R.id.mintegral_full_screen_ll_root);
////            if (!TextUtils.isEmpty(campaign.getIconUrl())) {
////                ImagePhotoLoad.loadCommonImg(context,campaign.getIconUrl(),mIvIcon);
////            }
////            if (!TextUtils.isEmpty(campaign.getImageUrl())) {
////                ImagePhotoLoad.loadCommonImg(context,campaign.getImageUrl(),mIvImage);
////            }
////            mTvAppName.setText(campaign.getAppName() + "");
////            mTvAppDesc.setText(campaign.getAppDesc() + "");
////            mTvCta.setText(campaign.getAdCall());
////            int rating = (int) campaign.getRating();
////            mStarLayout.setRating(rating);
////            nativeHandle.registerView(mTvCta, campaign);
////            ViewGroup parent = (ViewGroup) view.getParent();
////            if (parent != null) {
////                parent.removeAllViews();
////            }
////            group.addView(view);
////        }
//    }

    public void preloadNative() {

//        MIntegralSDK sdk = MIntegralSDKFactory.getMIntegralSDK();
//        Map<String, Object> preloadMap = new HashMap<String, Object>();
//        preloadMap.put(MIntegralConstans.PROPERTIES_LAYOUT_TYPE, MIntegralConstans.LAYOUT_NATIVE);
//        preloadMap.put(MIntegralConstans.PROPERTIES_UNIT_ID, Constants.Mob_UNIT_SPLASH);
//
//        List<NativeListener.Template> list = new ArrayList<NativeListener.Template>();
//        list.add(new NativeListener.Template(MIntegralConstans.TEMPLATE_BIG_IMG, 1));
//        preloadMap.put(MIntegralConstans.NATIVE_INFO, MtgNativeHandler.getTemplateString(list));
//        sdk.preload(preloadMap);

    }
    @Override
    public  void launchAds(final FragmentActivity context, final RelativeLayout group, RelativeLayout skipView, final Handler handler){
        //上架GP版本SDK无activity参数
        try{
//            mbSplashHandler = new MBSplashHandler(Constants.Mob_SPLASH_UNIT, Constants.Mob_SPLASH_UNIT_PLACE);
//            mbSplashHandler.setLoadTimeOut(3000);
//            Button textView = new Button(context);
//            textView.setText("logo");
//            mbSplashHandler.setLogoView(textView, 100, 100);
//
//            mbSplashHandler.setSplashLoadListener(new MBSplashLoadListener() {
//                @Override
//                public void onLoadSuccessed(MBridgeIds ids, int reqType) {
//                    LogUtil.i( " MobvistaSplashAds onLoadSuccessed: "+ids);
//                }
//
//                @Override
//                public void onLoadFailed(MBridgeIds ids, String msg,int reqType) {
//                    LogUtil.i( " MobvistaSplashAds onLoadFailed: "+ids);
//                    noAds(context,handler, AdsContext.AdsFrom.MOBVISTA,0);
//                }
//
//                @Override
//                public void isSupportZoomOut(MBridgeIds mBridgeIds, boolean b) {
//
//                }
//            });
//
//            mbSplashHandler.setSplashShowListener(new MBSplashShowListener() {
//                @Override
//                public void onShowSuccessed(MBridgeIds ids) {
//                    LogUtil.i( " MobvistaSplashAds onShowSuccessed: "+ids);
//                }
//
//                @Override
//                public void onShowFailed(MBridgeIds ids, String msg) {
//                    LogUtil.i( " MobvistaSplashAds onShowFailed: "+msg+ids);
//                    noAds(context,handler, AdsContext.AdsFrom.MOBVISTA,0);
//                }
//
//                @Override
//                public void onAdClicked(MBridgeIds ids) {
//                    clickAds(context, handler, AdsContext.AdsFrom.MOBVISTA);
//                }
//
//                @Override
//                public void onDismiss(MBridgeIds ids, int type) {
//                    closeAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
//                    EventBusUtil.getEventBus().post(new SplashAdDissmisEvent(AdsContext.AdsFrom.MOBVISTA.desc));
//                }
//
//                @Override
//                public void onAdTick(MBridgeIds ids, long millisUntilFinished) {
//                }
//
//                @Override
//                public void onZoomOutPlayStart(MBridgeIds mBridgeIds) {
//
//                }
//
//                @Override
//                public void onZoomOutPlayFinish(MBridgeIds mBridgeIds) {
//
//                }
//            });
//            mbSplashHandler.loadAndShow(group);
        } catch (Throwable e) {
            noAds(context,handler, AdsContext.AdsFrom.MOBVISTA,0);
            LogUtil.e(e);
        }
    }
}
