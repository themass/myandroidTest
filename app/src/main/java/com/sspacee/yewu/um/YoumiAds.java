//package com.sspacee.yewu.um;
//
//import android.content.Context;
//import android.os.Handler;
//import android.os.Message;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.RelativeLayout;
//import android.widget.Toast;
//
//import com.sspacee.common.util.DeviceInfoUtils;
//import com.sspacee.common.util.LogUtil;
//import com.sspacee.yewu.ads.base.AdsControllerInte;
//import com.sspacee.yewu.ads.base.NativeAdsReadyListener;
//import com.timeline.vpn.R;
//import com.timeline.vpn.ui.main.MainFragmentViewPage;
//
//import net.youmi.android.AdManager;
//import net.youmi.android.listener.Interface_ActivityListener;
//import net.youmi.android.nm.bn.BannerManager;
//import net.youmi.android.nm.bn.BannerViewListener;
//import net.youmi.android.nm.cm.ErrorCode;
//import net.youmi.android.nm.sp.SplashViewSettings;
//import net.youmi.android.nm.sp.SpotListener;
//import net.youmi.android.nm.sp.SpotManager;
//import net.youmi.android.nm.sp.SpotRequestListener;
//import net.youmi.android.nm.vdo.VideoAdListener;
//import net.youmi.android.nm.vdo.VideoAdManager;
//import net.youmi.android.nm.vdo.VideoAdRequestListener;
//import net.youmi.android.nm.vdo.VideoAdSettings;
//import net.youmi.android.nm.vdo.VideoInfoViewBuilder;
//import net.youmi.android.os.EarnPointsOrderList;
//import net.youmi.android.os.OffersBrowserConfig;
//import net.youmi.android.os.OffersManager;
//import net.youmi.android.os.PointsEarnNotify;
//import net.youmi.android.os.PointsManager;
//
///**
// * Created by themass on 2017/9/14.
// */
//
//public class YoumiAds implements AdsControllerInte {
//    private static final String publiserId = "8521b58e067666ba";
//    private static final String appSecret = "a6daf867d009598d";
//    private static boolean preloadVideoOk = false;
//    public static PointsEarnNotify pointsListener = new PointsEarnNotify() {
//        public void onPointEarn(Context var1, EarnPointsOrderList var2) {
//            Toast.makeText(var1, R.string.offerads_ths, Toast.LENGTH_SHORT).show();
//        }
//    };
//    public  void init(Context context) {
//        AdManager.getInstance(context).init(publiserId, appSecret, true);
//        VideoAdManager.getInstance(context).setUserId(DeviceInfoUtils.getDeviceId(context));
//        SpotManager.getInstance(context).setImageType(SpotManager.IMAGE_TYPE_VERTICAL);
//        SpotManager.getInstance(context)
//                .setAnimationType(SpotManager.ANIMATION_TYPE_ADVANCED);
//        preloadVideoAd(context);
//        // 如果使用积分广告，请务必调用积分广告的初始化接口:
//        OffersManager.getInstance(context).onAppLaunch();
//        PointsManager.getInstance(context).registerPointsEarnNotify(pointsListener);
//        // (可选)设置是否在通知栏显示下载相关提示。默认为true，标识开启；设置为false则关闭。（sdk v4.10版本新增功能）
//        //AdManager.getInstance(context).setIsDownloadTipsDisplayOnNotification(true);
//
//        // (可选)设置安装完成后是否在通知栏显示已安装成功的通知。默认为true，标识开启；设置为false则关闭。（sdk v4.10版本新增功能）
//        // AdManager.getInstance(this).setIsInstallationSuccessTipsDisplayOnNotification(false);
//
//        // (可选)设置是否在通知栏显示积分赚取提示。默认为true，标识开启；设置为false则关闭。
//        // 如果开发者采用了服务器回调积分的方式，那么本方法将不会生效
//         PointsManager.getInstance(context).setEnableEarnPointsNotification(false);
//
//        // (可选)设置是否开启积分赚取的Toast提示。默认为true，标识开启；设置为false这关闭。
//        // 如果开发者采用了服务器回调积分的方式，那么本方法将不会生效
//         PointsManager.getInstance(context).setEnableEarnPointsToastTips(false);
//        // userid 不能为空 或者 空串,否则设置无效, 字符串长度必须要小于50
//
//    }
//
//    /**
//     * 预加载广告
//     */
//    public  void preloadVideoAd(Context context) {
//        // 注意：不必每次展示插播广告前都请求，只需在应用启动时请求一次
//        SpotManager.getInstance(context).requestSpot(new SpotRequestListener() {
//            @Override
//            public void onRequestSuccess() {
//                preloadVideoOk = true;
//                LogUtil.i("preloadVideoAd 请求插播广告成功");
//            }
//
//            @Override
//            public void onRequestFailed(int errorCode) {
//                preloadVideoOk = false;
//                errorHandle("preloadVideoAd", errorCode);
//            }
//        });
//        VideoAdManager.getInstance(context)
//                .requestVideoAd(context, new VideoAdRequestListener() {
//                    @Override
//                    public void onRequestSuccess() {
//                        LogUtil.i("请求视频广告成功");
//                    }
//                    @Override
//                    public void onRequestFailed(int errorCode) {
//                       errorHandle("requestVideoAd",errorCode);
//                    }
//                });
//    }
//    @Override
//    public  void bannerAds(final Context context, final ViewGroup group,  final Handler handler) {
//        final View bannerView = BannerManager.getInstance(context)
//                .getBannerView(context, new BannerViewListener() {
//                    @Override
//                    public void onRequestSuccess() {
//                        Message msg = Message.obtain();
//                        msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_BANNER, AdsContext.AdsShowStatus.ADS_READY_MSG, AdsContext.AdsFrom.YOUMI);
//                        handler.sendMessage(msg);
//                    }
//                    @Override
//                    public void onSwitchBanner() {
//                        LogUtil.i("广告条切换");
//                    }
//                    @Override
//                    public void onRequestFailed() {
//                        Message msg = Message.obtain();
//                        msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_BANNER, AdsContext.AdsShowStatus.ADS_NO_MSG, AdsContext.AdsFrom.YOUMI);
//                        handler.sendMessage(msg);
//                    }
//                });
//        if (bannerView != null) {
//            ViewGroup parent = (ViewGroup) bannerView.getParent();
//            if (parent != null) {
//                parent.removeAllViews();
//            }
//        }
//        group.addView(bannerView);
//    }
//    @Override
//    public void exitApp(Context context){
//        // 插屏广告（包括普通插屏广告、轮播插屏广告、原生插屏广告）
//        SpotManager.getInstance(context).onAppExit();
//        // 视频广告（包括普通视频广告、原生视频广告）
//        VideoAdManager.getInstance(context).onAppExit();
//        PointsManager.getInstance(context).unRegisterPointsEarnNotify(pointsListener);
//        OffersManager.getInstance(context).onAppExit();
//    }
//    @Override
//    public void lanchExit(Context context){
//        SpotManager.getInstance(context).onDestroy();
//    }
//    @Override
//    public void launchAds(final Context mContext, RelativeLayout group, RelativeLayout skipView, final Handler handler) {
//        try {
//
//            // 对开屏进行设置
//            SplashViewSettings splashViewSettings = new SplashViewSettings();
//            //		// 设置是否展示失败自动跳转，默认自动跳转
//            splashViewSettings.setAutoJumpToTargetWhenShowFailed(false);
//            splashViewSettings.setTargetClass(MainFragmentViewPage.class);
//            // 设置开屏的容器
//            splashViewSettings.setSplashViewContainer(group);
//            // 展示开屏广告
//            SpotManager.getInstance(mContext)
//                    .showSplash(mContext, splashViewSettings, new SpotListener() {
//                        @Override
//                        public void onShowSuccess() {
//                            Message msg = Message.obtain();
//                            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_SPREAD, AdsContext.AdsShowStatus.ADS_READY_MSG, AdsContext.AdsFrom.YOUMI);
//                            handler.sendMessage(msg);
//                        }
//
//                        @Override
//                        public void onShowFailed(int errorCode) {
//                            Message msg = Message.obtain();
//                            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_SPREAD, AdsContext.AdsShowStatus.ADS_NO_MSG, AdsContext.AdsFrom.YOUMI);
//                            handler.sendMessage(msg);
//                            errorHandle("launchAds", errorCode);
//
//                        }
//
//                        @Override
//                        public void onSpotClosed() {
//                            Message msg = Message.obtain();
//                            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_SPREAD, AdsContext.AdsShowStatus.ADS_DISMISS_MSG, AdsContext.AdsFrom.YOUMI);
//                            handler.sendMessage(msg);
//                        }
//
//                        @Override
//                        public void onSpotClicked(boolean isWebPage) {
//                            Message msg = Message.obtain();
//                            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_SPREAD, AdsContext.AdsShowStatus.ADS_CLICK_MSG, AdsContext.AdsFrom.YOUMI);
//                            handler.sendMessage(msg);
//                        }
//                    });
//        } catch (Exception e) {
//            LogUtil.e(e);
//            Message msg = Message.obtain();
//            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_SPREAD, AdsContext.AdsShowStatus.ADS_NO_MSG, AdsContext.AdsFrom.YOUMI);
//            handler.sendMessage(msg);
//        }
//    }
//    @Override
//    public void interstitialAds(final Context context, final Handler handler,final boolean score) {
//        // 展示轮播插屏广告
//        try {
//            SpotManager.getInstance(context)
//                    .showSlideableSpot(context, new SpotListener() {
//
//                        @Override
//                        public void onShowSuccess() {
//                            Message msg = Message.obtain();
//                            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_READY_MSG, AdsContext.AdsFrom.YOUMI,score);
//                            handler.sendMessage(msg);
//                        }
//
//                        @Override
//                        public void onShowFailed(int errorCode) {
//                            Message msg = Message.obtain();
//                            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_NO_MSG, AdsContext.AdsFrom.YOUMI,score);
//                            handler.sendMessage(msg);
//                            errorHandle("interstitialAds", errorCode);
//                        }
//
//                        @Override
//                        public void onSpotClosed() {
//                            Message msg = Message.obtain();
//                            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_DISMISS_MSG, AdsContext.AdsFrom.YOUMI);
//                            handler.sendMessage(msg);
//                        }
//
//                        @Override
//                        public void onSpotClicked(boolean isWebPage) {
//                            Message msg = Message.obtain();
//                            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_CLICK_MSG, AdsContext.AdsFrom.YOUMI);
//                            handler.sendMessage(msg);
//                        }
//                    });
//        } catch (Exception e) {
//            LogUtil.e(e);
//            Message msg = Message.obtain();
//            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_NO_MSG, AdsContext.AdsFrom.YOUMI);
//            handler.sendMessage(msg);
//        }
//    }
//    @Override
//    public View nativeIntersAds(final Context context, final Handler handler) {
//        try {
//            View nativeSpotView = SpotManager.getInstance(context)
//                    .getNativeSpot(context, new SpotListener() {
//
//                        @Override
//                        public void onShowSuccess() {
//                            Message msg = Message.obtain();
//                            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_NATIVE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_READY_MSG, AdsContext.AdsFrom.YOUMI);
//                            handler.sendMessage(msg);
//                        }
//
//                        @Override
//                        public void onShowFailed(int errorCode) {
//                            Message msg = Message.obtain();
//                            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_NATIVE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_NO_MSG, AdsContext.AdsFrom.YOUMI);
//                            handler.sendMessage(msg);
//                            errorHandle("nativeAds", errorCode);
//                        }
//
//                        @Override
//                        public void onSpotClosed() {
//                            Message msg = Message.obtain();
//                            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_NATIVE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_DISMISS_MSG, AdsContext.AdsFrom.YOUMI);
//                            handler.sendMessage(msg);
//                        }
//
//                        @Override
//                        public void onSpotClicked(boolean isWebPage) {
//                            Message msg = Message.obtain();
//                            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_NATIVE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_CLICK_MSG, AdsContext.AdsFrom.YOUMI);
//                            handler.sendMessage(msg);
//                        }
//                    });
//            return nativeSpotView;
//        } catch (Throwable e) {
//            Message msg = Message.obtain();
//            msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_NATIVE_INTERSTITIAL, AdsContext.AdsShowStatus.ADS_NO_MSG, AdsContext.AdsFrom.YOUMI);
//            handler.sendMessage(msg);
//            LogUtil.e("原声广告fail：", e);
//        }
//        return null;
//    }
//    @Override
//    public void nativeVideoAds(final Context context, final Handler handler, final ViewGroup group) {
//        group.setVisibility(View.GONE);
//        VideoAdManager.getInstance(context).onPause();
//        VideoAdManager.getInstance(context).onStart();
//        VideoAdManager.getInstance(context).onDestroy();
//        final VideoAdSettings videoAdSettings = new VideoAdSettings();
//        //		// 只需要调用一次，由于在主页窗口中已经调用了一次，所以此处无需调用
//        //		VideoAdManager.getInstance().requestVideoAd(mContext);
//        // 设置信息流视图，将图标，标题，描述，下载按钮对应的ID传入
//        final ViewGroup mVideoInfoLayout = (ViewGroup) group.findViewById(R.id.rl_video_info);
//        final RelativeLayout mNativeVideoAdLayout = (RelativeLayout) group.findViewById(R.id.rl_native_video_ad);
//        final VideoInfoViewBuilder videoInfoViewBuilder = VideoAdManager.getInstance(context)
//                .getVideoInfoViewBuilder(
//                        context).setRootContainer(mVideoInfoLayout)
//                .bindAppIconView(R.id
//                        .info_iv_icon)
//                .bindAppNameView(R.id
//                        .info_tv_title)
//                .bindAppDescriptionView(R.id
//                        .info_tv_description)
//                .bindDownloadButton(R.id
//                        .info_btn_download);
//        View nativeVideoAdView = VideoAdManager.getInstance(context)
//                .getNativeVideoAdView(context,
//                        videoAdSettings,
//                        new VideoAdListener() {
//                            @Override
//                            public void onPlayStarted() {
//                                group.setVisibility(View.VISIBLE);
//                                // 展示视频信息流视图
//                                mVideoInfoLayout.setVisibility(View.VISIBLE);
//                                Message msg = Message.obtain();
//                                msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_NATIVE_VEDIO, AdsContext.AdsShowStatus.ADS_READY_MSG, AdsContext.AdsFrom.YOUMI);
//                                handler.sendMessage(msg);
//                            }
//
//                            @Override
//                            public void onPlayInterrupted() {
//                                mVideoInfoLayout.setVisibility(View.GONE);
//                                group.setVisibility(View.GONE);
////                                // 释放资源
////                                if (videoInfoViewBuilder != null) {
////                                    videoInfoViewBuilder.release();
////                                }
////                                // 移除原生视频控件
////                                if (mNativeVideoAdLayout != null) {
////                                    mNativeVideoAdLayout.removeAllViews();
////                                    group.setVisibility(View.GONE);
////                                }
//                            }
//
//                            @Override
//                            public void onPlayFailed(int errorCode) {
//                                Message msg = Message.obtain();
//                                msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_NATIVE_VEDIO, AdsContext.AdsShowStatus.ADS_NO_MSG, AdsContext.AdsFrom.YOUMI);
//                                handler.sendMessage(msg);
//                                errorHandle("nativeVideoAds", errorCode);
//                            }
//
//                            @Override
//                            public void onPlayCompleted() {
//                                Message msg = Message.obtain();
//                                msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_NATIVE_VEDIO, AdsContext.AdsShowStatus.ADS_FINISH_MSG, AdsContext.AdsFrom.YOUMI);
//                                handler.sendMessage(msg);
////                                if (videoInfoViewBuilder != null) {
////                                    videoInfoViewBuilder.release();
////                                }
////                                // 移除原生视频控件
////                                if (mNativeVideoAdLayout != null) {
////                                    mNativeVideoAdLayout.removeAllViews();
////                                }
////                                group.setVisibility(View.GONE);
//                            }
//
//                        }
//                );
//        if (mNativeVideoAdLayout != null) {
//            final RelativeLayout.LayoutParams params =
//                    new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
//                            ViewGroup.LayoutParams.WRAP_CONTENT
//                    );
//            if (nativeVideoAdView != null) {
//                mNativeVideoAdLayout.removeAllViews();
//                // 添加原生视频广告
//                mNativeVideoAdLayout.addView(nativeVideoAdView, params);
//                mNativeVideoAdLayout.setVisibility(View.VISIBLE);
//            }
//        }
//    }
//
//    @Override
//    public void nativeAds(Context context, Handler handler,NativeAdsReadyListener listener) {
//
//    }
//
//    @Override
//    public void onStart(Context context) {
//        VideoAdManager.getInstance(context).onStart();
//    }
//
//    @Override
//    public void onResume(Context context) {
//        VideoAdManager.getInstance(context).onResume();
//    }
//
//    @Override
//    public void onPause(Context context) {
//        SpotManager.getInstance(context).onPause();
//        VideoAdManager.getInstance(context).onPause();
//    }
//
//    @Override
//    public void onStop(Context context) {
//        SpotManager.getInstance(context).onStop();
//        VideoAdManager.getInstance(context).onStop();
//    }
//
//    @Override
//    public void onDestroy(Context context) {
//        SpotManager.getInstance(context).onDestroy();
//        VideoAdManager.getInstance(context).onDestroy();
//        BannerManager.getInstance(context).onDestroy();
//
//    }
//    public  void bannerExit(Context context,ViewGroup view){
//        if(view!=null){
//            view.removeAllViews();
//        }
//        BannerManager.getInstance(context).onDestroy();
//    }
//    public static void errorHandle(String title, int errorCode) {
//        LogUtil.e("错误方法：" + title);
//        switch (errorCode) {
//            case ErrorCode.NON_NETWORK:
//                LogUtil.e("网络错误");
//                break;
//            case ErrorCode.NON_AD:
//                LogUtil.e("暂无开屏广告");
//                break;
//            case ErrorCode.RESOURCE_NOT_READY:
//                LogUtil.e("开屏资源还没准备好");
//                break;
//            case ErrorCode.SHOW_INTERVAL_LIMITED:
//                LogUtil.e("开屏展示间隔限制");
//                break;
//            case ErrorCode.WIDGET_NOT_IN_VISIBILITY_STATE:
//                LogUtil.e("开屏控件处在不可见状态");
//                break;
//            default:
//                LogUtil.e(String.format("errorCode: %d", errorCode));
//                break;
//        }
//    }
//    @Override
//    public void offerAds(Context context,final Handler handler){
//        OffersManager.getInstance(context).setCustomUserId(DeviceInfoUtils.getDeviceId(context));
//        // 有米Android SDK v4.10之后的sdk还需要配置下面代码，以告诉sdk使用了服务器回调
//        OffersManager.getInstance(context).setUsingServerCallBack(true);
//        OffersBrowserConfig.getInstance(context).setBrowserTitleText(context.getResources().getString(R.string.menu_btn_support));
//        // 设置积分墙标题背景颜色
//        OffersBrowserConfig.getInstance(context).setBrowserTitleBackgroundColor(context.getResources().getColor(R.color.style_color_primary));
//        // 设置积分余额区域是否显示
//        // true ：显示（默认值）
//        // false：不显示
//        OffersBrowserConfig.getInstance(context).setPointsLayoutVisibility(false);
//        checkConfig(context);
//
//        OffersManager.getInstance(context).showOffersWall(new Interface_ActivityListener() {
//            /**
//             * 当积分墙销毁的时候，即积分墙的Activity调用了onDestory的时候回调
//             */
//            @Override
//            public void onActivityDestroy(Context context) {
//                Message msg = Message.obtain();
//                msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_OFFER, AdsContext.AdsShowStatus.ADS_DISMISS_MSG, AdsContext.AdsFrom.YOUMI);
//                handler.sendMessage(msg);
////                PointsManager.getInstance(context).awardPoints(10.0f);
//                LogUtil.i("退出积分墙");
//            }
//        });
//    }
//    @Override
//    public void videoAds(Context context,Handler handler){
//        final VideoAdSettings videoAdSettings = new VideoAdSettings();
//        VideoAdManager.getInstance(context)
//                .showVideoAd(context, videoAdSettings, new VideoAdListener() {
//                    @Override
//                    public void onPlayStarted() {
//                        LogUtil.i("开始播放视频");
//                    }
//
//                    @Override
//                    public void onPlayInterrupted() {
//                        LogUtil.i("播放视频被中断");
//                    }
//
//                    @Override
//                    public void onPlayFailed(int errorCode) {
//                        LogUtil.i("视频播放失败");
//                        errorHandle("videoAds",errorCode);
//                    }
//
//                    @Override
//                    public void onPlayCompleted() {
//                        LogUtil.i("视频播放成功");
//                    }
//                });
//    }
//    private void checkConfig(Context context) {
//        StringBuilder sb = new StringBuilder();
//
//        addTextToSb(sb,
//                OffersManager.getInstance(context).checkOffersAdConfig() ? "广告配置结果：正常" :
//                        "广告配置结果：异常，具体异常请查看Log，Log标签：YoumiSdk"
//        );
//        addTextToSb(sb, "%s服务器回调", OffersManager.getInstance(context).isUsingServerCallBack() ? "已经开启" : "没有开启");
//        addTextToSb(sb,
//                "%s通知栏下载相关的通知",
//                AdManager.getInstance(context).isDownloadTipsDisplayOnNotification() ? "已经开启" : "没有开启"
//        );
//        addTextToSb(sb,
//                "%s通知栏安装成功的通知",
//                AdManager.getInstance(context).isInstallationSuccessTipsDisplayOnNotification() ? "已经开启" : "没有开启"
//        );
//        addTextToSb(sb,
//                "%s通知栏赚取积分的提示",
//                PointsManager.getInstance(context).isEnableEarnPointsNotification() ? "已经开启" : "没有开启"
//        );
//        addTextToSb(sb,
//                "%s积分赚取的Toast提示",
//                PointsManager.getInstance(context).isEnableEarnPointsToastTips() ? "已经开启" : "没有开启"
//        );
//        LogUtil.i(sb.toString());
//    }
//
//    /**
//     * 格式化字符串
//     */
//    private void addTextToSb(StringBuilder sb, String format, Object... args) {
//        sb.append(String.format(format, args));
//        sb.append(System.getProperty("line.separator"));
//    }
//
//}
