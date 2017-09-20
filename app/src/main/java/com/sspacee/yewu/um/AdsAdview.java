//package com.sspacee.yewu.um;
//
//import android.content.Context;
//import android.os.Handler;
//import android.os.Message;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.RelativeLayout;
//
//import com.kyview.InitConfiguration;
//import com.kyview.interfaces.AdViewInstlListener;
//import com.kyview.interfaces.AdViewNativeListener;
//import com.kyview.interfaces.AdViewSpreadListener;
//import com.kyview.manager.AdViewInstlManager;
//import com.kyview.manager.AdViewNativeManager;
//import com.kyview.manager.AdViewSpreadManager;
//import com.kyview.natives.NativeAdInfo;
//import com.sspacee.common.util.LogUtil;
//import com.sspacee.yewu.ads.base.AdsControllerInte;
//import com.sspacee.yewu.um.AdsContext;
//import com.sspacee.yewu.ads.base.NativeAdsReadyListener;
//import com.timeline.vpn.R;
//import com.timeline.vpn.ui.main.MainFragmentViewPage;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import static com.sspacee.yewu.ads.adview.AdviewConstant.ADS_ADVIEW_KEY;
//import static com.sspacee.yewu.ads.adview.AdviewConstant.adsKeySet;
//import static com.sspacee.yewu.ads.adview.AdviewConstant.adsKeySet1;
//
///**
// * Created by themass on 2016/8/19.
// */
//public class AdsAdview implements AdsControllerInte {
//
//    private static InitConfiguration initConfig;
//    private static void initConfig(Context context) {
//        InitConfiguration.Builder builder = new InitConfiguration.Builder(context)
//                .setUpdateMode(InitConfiguration.UpdateMode.EVERYTIME)   // 实时获取配置
//                .setBannerCloseble(InitConfiguration.BannerSwitcher.DEFAULT)    //横幅可关闭按钮
//                .setInstlDisplayType(InitConfiguration.InstlDisplayType.DIALOG_MODE)// 为默认情况,设置插屏展示模式，popupwindow模式可设置窗体外可点击
//                .setInstlCloseble(InitConfiguration.InstlSwitcher.CANCLOSED);     //插屏可关闭按钮
//        builder.setAdYoumiSize(InitConfiguration.AdYoumiSize.FIT_SCREEN);
//
//        builder.setYoumiIntent(MainFragmentViewPage.class);
//        builder.setAdMobSize(InitConfiguration.AdMobSize.BANNER);
//        builder.setAdGdtSize(InitConfiguration.AdGdtSize.BANNER);
//        builder.setAdSize(InitConfiguration.AdSize.BANNER_SMART);
////        if (MyApplication.isDebug) {
////            builder.setRunMode(InitConfiguration.RunMode.TEST);
////        } else {
////            builder.setRunMode(InitConfiguration.RunMode.NORMAL);
////        }
//        builder.setRunMode(InitConfiguration.RunMode.NORMAL);
//        initConfig = builder.build();
//    }
//    @Override
//    public  void init(Context context) {
//        try {
//            initConfig(context);
//
//        } catch (Throwable e) {
//
//            LogUtil.e(e);
//        }
//    }
//    @Override
//    public void launchAds(final Context context, RelativeLayout group, RelativeLayout skipView, final Handler handler) {
//        try {
//            if (group == null) {
//                AdViewSpreadManager.getInstance(context).destroySpread(ADS_ADVIEW_KEY);
//                return;
//            }
//            AdViewSpreadManager.getInstance(context).init(initConfig, adsKeySet);
//            AdViewSpreadManager.getInstance(context).setSpreadLogo(R.drawable.ic_trans_logo);
//            AdViewSpreadManager.getInstance(context).request(context, ADS_ADVIEW_KEY, new AdViewSpreadListener() {
//                @Override
//                public void onAdClick(String s) {
//                    Message msg = Message.obtain();
//                    msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_SPREAD, AdsContext.AdsShowStatus.ADS_CLICK_MSG, AdsContext.AdsFrom.ADVIEW);
//                    handler.sendMessage(msg);
//                }
//
//                @Override
//                public void onAdDisplay(String s) {
//                    Message msg = Message.obtain();
//                    msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_SPREAD, AdsContext.AdsShowStatus.ADS_PRESENT_MSG, AdsContext.AdsFrom.ADVIEW);
//                    handler.sendMessage(msg);
//                }
//
//                @Override
//                public void onAdClose(String s) {
//                    Message msg = Message.obtain();
//                    msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_SPREAD, AdsContext.AdsShowStatus.ADS_DISMISS_MSG, AdsContext.AdsFrom.ADVIEW);
//                    handler.sendMessage(msg);
//                }
//
//                @Override
//                public void onAdRecieved(String s) {
//                    Message msg = Message.obtain();
//                    msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_SPREAD, AdsContext.AdsShowStatus.ADS_READY_MSG, AdsContext.AdsFrom.ADVIEW);
//                    handler.sendMessage(msg);
//                }
//
//                @Override
//                public void onAdFailed(String s) {
//                    Message msg = Message.obtain();
//                    msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_SPREAD, AdsContext.AdsShowStatus.ADS_NO_MSG, AdsContext.AdsFrom.ADVIEW);
//                    handler.sendMessage(msg);
//                }
//
//                @Override
//                public void onAdSpreadNotifyCallback(String key, ViewGroup view, int ruleTime, int delayTime) {
//                    LogUtil.i("ruleTime:" + ruleTime + ";delayTime:" + delayTime);
//                }
//            }, group, skipView);
//        } catch (Throwable e) {
//            Message msg = Message.obtain();
//            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_SPREAD, AdsContext.AdsShowStatus.ADS_NO_MSG, AdsContext.AdsFrom.ADVIEW);
//            handler.sendMessage(msg);
//            LogUtil.e(e);
//        }
//    }
//    @Override
//    public void interstitialAds(final Context context, final Handler handler,final boolean score) {
//        try {
//            LogUtil.i("adview interstitialAds req");
//            AdViewInstlManager.getInstance(context).requestAd(context, ADS_ADVIEW_KEY_TEST, new AdViewInstlListener() {
//
//                @Override
//                public void onAdClick(String s) {
//                    Message msg = Message.obtain();
//                    msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_CLICK_MSG, AdsContext.AdsFrom.ADVIEW);
//                    handler.sendMessage(msg);
//                }
//
//                @Override
//                public void onAdDisplay(String s) {
//                    Message msg = Message.obtain();
//                    msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_PRESENT_MSG, AdsContext.AdsFrom.ADVIEW);
//                    handler.sendMessage(msg);
//                }
//
//                @Override
//                public void onAdDismiss(String s) {
//                    Message msg = Message.obtain();
//                    msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_DISMISS_MSG, AdsContext.AdsFrom.ADVIEW);
//                    handler.sendMessage(msg);
//                }
//
//                @Override
//                public void onAdRecieved(String s) {
//                    Message msg = Message.obtain();
//                    msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_READY_MSG, AdsContext.AdsFrom.ADVIEW,score);
//                    handler.sendMessage(msg);
//                    AdViewInstlManager.getInstance(context)
//                            .showAd(context, ADS_ADVIEW_KEY_TEST);
//                }
//
//                @Override
//                public void onAdFailed(String s) {
//                    Message msg = Message.obtain();
//                    msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_NO_MSG, AdsContext.AdsFrom.ADVIEW);
//                    handler.sendMessage(msg);
//                }
//            });
//        } catch (Throwable e) {
//            Message msg = Message.obtain();
//            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_NO_MSG, AdsContext.AdsFrom.ADVIEW);
//            handler.sendMessage(msg);
//            LogUtil.e(e);
//        }
//    }
//
//    @Override
//    public void bannerAds(final Context context, final ViewGroup group,  final Handler handler) {
//        try {
//
//    }
//    @Override
//    public  void nativeAds(final Context context, final Handler handler, final NativeAdsReadyListener listener) {
//        try {
//            AdViewNativeManager.getInstance(context).requestAd(context, ADS_ADVIEW_KEY, 10, new AdViewNativeListener() {
//
//                @Override
//                public void onAdFailed(String s) {
//                    Message msg = Message.obtain();
//                    msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_NATIVE, AdsContext.AdsShowStatus.ADS_NO_MSG, AdsContext.AdsFrom.ADVIEW);
//                    handler.sendMessage(msg);
//                 }
//
//                @Override
//                public void onAdRecieved(String s, ArrayList arrayList) {
//                    Message msg = Message.obtain();
//                    msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_NATIVE, AdsContext.AdsShowStatus.ADS_READY_MSG, AdsContext.AdsFrom.ADVIEW);
//                    handler.sendMessage(msg);
//                    if (listener != null) {
//                        listener.onAdRecieved((List<NativeAdInfo>) arrayList);
//                    }
//                }
//
//                @Override
//                public void onAdStatusChanged(String s, int i) {
//                    Message msg = Message.obtain();
//                    msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_NATIVE, AdsContext.AdsShowStatus.ADS_CLICK_MSG, AdsContext.AdsFrom.ADVIEW);
//                    handler.sendMessage(msg);
//                }
//            }); //设置原生回调接口
//        } catch (Throwable e) {
//            Message msg = Message.obtain();
//            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_NATIVE, AdsContext.AdsShowStatus.ADS_NO_MSG, AdsContext.AdsFrom.ADVIEW);
//            handler.sendMessage(msg);
//            LogUtil.e("原声广告fail：", e);
//        }
//    }
//
//    @Override
//    public void exitApp(Context context) {
//
//    }
//
//    @Override
//    public void lanchExit(Context context) {
//
//    }
//
//    @Override
//    public View nativeIntersAds(Context context, Handler handler) {
//        return null;
//    }
//
//    @Override
//    public void nativeVideoAds(Context context, Handler handler, ViewGroup group) {
//
//    }
//
//    @Override
//    public void onStart(Context context) {
//
//    }
//
//    @Override
//    public void onResume(Context context) {
//
//    }
//
//    @Override
//    public void onPause(Context context) {
//
//    }
//
//    @Override
//    public void onStop(Context context) {
//
//    }
//
//    @Override
//    public void onDestroy(Context context) {
//
//    }
//    @Override
//    public  void bannerExit(Context context,ViewGroup view){
//        if(view!=null){
//            view.removeAllViews();
//        }
//    }
//    @Override
//    public void offerAds(Context context,final Handler handler){
//        Message msg = Message.obtain();
//        msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_OFFER, AdsContext.AdsShowStatus.ADS_DISMISS_MSG, AdsContext.AdsFrom.ADVIEW);
//        handler.sendMessage(msg);
//    }
//    @Override
//    public void videoAds(Context context,Handler handler){}
//}
