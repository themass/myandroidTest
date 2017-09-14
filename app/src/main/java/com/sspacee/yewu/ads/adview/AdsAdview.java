package com.sspacee.yewu.ads.adview;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.kyview.InitConfiguration;
import com.kyview.interfaces.AdViewBannerListener;
import com.kyview.interfaces.AdViewInstlListener;
import com.kyview.interfaces.AdViewNativeListener;
import com.kyview.interfaces.AdViewSpreadListener;
import com.kyview.interfaces.AdViewVideoListener;
import com.kyview.manager.AdViewBannerManager;
import com.kyview.manager.AdViewInstlManager;
import com.kyview.manager.AdViewNativeManager;
import com.kyview.manager.AdViewSpreadManager;
import com.kyview.manager.AdViewVideoManager;
import com.kyview.natives.NativeAdInfo;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.task.ScoreTask;
import com.timeline.vpn.ui.main.MainFragmentViewPage;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by themass on 2016/8/19.
 */
public class AdsAdview {
    public static final int what1_video=2;
    public static final int what1_interstitial=3;
    public static HandlerThread adsMsgThread = new HandlerThread("ads_msg_back");
    private static InitConfiguration initConfig;

    static {
        adsMsgThread.start();
    }

    public static void initConfig(Context context) {
        InitConfiguration.Builder builder = new InitConfiguration.Builder(context)
                .setUpdateMode(InitConfiguration.UpdateMode.EVERYTIME)   // 实时获取配置
                .setBannerCloseble(InitConfiguration.BannerSwitcher.DEFAULT)    //横幅可关闭按钮
                .setInstlDisplayType(InitConfiguration.InstlDisplayType.DIALOG_MODE)// 为默认情况,设置插屏展示模式，popupwindow模式可设置窗体外可点击
                .setInstlCloseble(InitConfiguration.InstlSwitcher.CANCLOSED);     //插屏可关闭按钮
        builder.setAdYoumiSize(InitConfiguration.AdYoumiSize.SIZE_320x50);
        builder.setYoumiIntent(MainFragmentViewPage.class);
        builder.setAdMobSize(InitConfiguration.AdMobSize.BANNER);
        builder.setAdGdtSize(InitConfiguration.AdGdtSize.BANNER);
        builder.setAdSize(InitConfiguration.AdSize.BANNER_SMART);
//        if (MyApplication.isDebug) {
//            builder.setRunMode(InitConfiguration.RunMode.TEST);
//        } else {
//            builder.setRunMode(InitConfiguration.RunMode.NORMAL);
//        }
        builder.setRunMode(InitConfiguration.RunMode.NORMAL);
        initConfig = builder.build();
    }

    public static void init(Context context) {
        try {
            AdViewSpreadManager.getInstance(context).init(initConfig,
                    Constants.adsKeySet);
            AdViewBannerManager.getInstance(context).init(initConfig, Constants.adsKeySet);
            AdViewInstlManager.getInstance(context).init(initConfig, Constants.adsKeySet1);
            AdViewNativeManager.getInstance(context).init(initConfig, Constants.adsKeySet);
            AdViewVideoManager.getInstance(context).init(initConfig, Constants.adsKeySet);
        } catch (Throwable e) {
            AdsAdview.adsNotify(context, Constants.ADS_TYPE_INIT, Constants.ADS_TYPE_ERROR);
            LogUtil.e(e);
        }
    }

    public static void launchAds(final Context context, RelativeLayout group, RelativeLayout skipView, final Handler handler) {
        try {
            if (group == null) {
                AdViewSpreadManager.getInstance(context).destroySpread(Constants.ADS_ADVIEW_KEY);
                return;
            }
            AdViewSpreadManager.getInstance(context).init(initConfig, Constants.adsKeySet);
            AdViewSpreadManager.getInstance(context).setSpreadLogo(R.drawable.ic_trans_logo);
//            AdViewSpreadManager.getInstance(context).setSpreadNotifyType(AdViewSpreadManager.NOTIFY_COUNTER_CUSTOM);
            AdViewSpreadManager.getInstance(context).request(context, Constants.ADS_ADVIEW_KEY, new AdViewSpreadListener() {
                @Override
                public void onAdClick(String s) {
                    if(handler!=null)
                        handler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_SPREAD, Constants.ADS_CLICK_MSG);
                }

                @Override
                public void onAdDisplay(String s) {
                    if(handler!=null)
                        handler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_SPREAD, Constants.ADS_PRESENT_MSG);
                }

                @Override
                public void onAdClose(String s) {
                    if(handler!=null)
                        handler.sendEmptyMessage(Constants.ADS_DISMISS_MSG);
                }

                @Override
                public void onAdRecieved(String s) {
                    if(handler!=null)
                        handler.sendEmptyMessage(Constants.ADS_READY_MSG);
                }

                @Override
                public void onAdFailed(String s) {
                    if(handler!=null)
                        handler.sendEmptyMessage(Constants.ADS_NO_MSG);
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_SPREAD, Constants.ADS_NO_MSG);
                }

                @Override
                public void onAdSpreadNotifyCallback(String key, ViewGroup view, int ruleTime, int delayTime) {
                    LogUtil.i("ruleTime:" + ruleTime + ";delayTime:" + delayTime);
                }
            }, group, skipView);
        } catch (Throwable e) {
            AdsAdview.adsNotify(context, Constants.ADS_TYPE_SPREAD, Constants.ADS_TYPE_ERROR);
            LogUtil.e(e);
        }
    }

    public static void interstitialAds(final Context context, final Handler handler) {
        try {
            LogUtil.i("adview interstitialAds req");
            AdViewInstlManager.getInstance(context).requestAd(context, Constants.ADS_ADVIEW_KEY_TEST, new AdViewInstlListener() {

                @Override
                public void onAdClick(String s) {
                    if(handler!=null) {
                        Message msg = Message.obtain();
                        msg.what = Constants.ADS_CLICK_MSG;
                        msg.arg1 = what1_interstitial;
                        handler.sendMessage(msg);
                    }
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_INTERSTITIAL, Constants.ADS_CLICK_MSG);
                }

                @Override
                public void onAdDisplay(String s) {
                    if(handler!=null) {
                        Message msg = Message.obtain();
                        msg.what = Constants.ADS_PRESENT_MSG;
                        msg.arg1 = what1_interstitial;
                        handler.sendMessage(msg);
                    }
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_INTERSTITIAL, Constants.ADS_PRESENT_MSG);
                }

                @Override
                public void onAdDismiss(String s) {
                    if(handler!=null) {
                        Message msg = Message.obtain();
                        msg.what = Constants.ADS_DISMISS_MSG;
                        msg.arg1 = what1_interstitial;
                        handler.sendMessage(msg);
                    }
                }

                @Override
                public void onAdRecieved(String s) {
                    LogUtil.i("adview onAdRecieved:"+s);
                    if (handler != null){
                        Message msg = Message.obtain();
                        msg.what = Constants.ADS_READY_MSG;
                        msg.arg1 = what1_interstitial;
                        handler.sendMessage(msg);
                    }
                    AdViewInstlManager.getInstance(context)
                            .showAd(context, Constants.ADS_ADVIEW_KEY_TEST);
                }

                @Override
                public void onAdFailed(String s) {
                    LogUtil.e(s);
                    if(handler!=null) {
                        Message msg = Message.obtain();
                        msg.what = Constants.ADS_NO_MSG;
                        msg.arg1 = what1_interstitial;
                        handler.sendMessage(msg);
                    }
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_INTERSTITIAL, Constants.ADS_NO_MSG);
                }
            });
        } catch (Throwable e) {
            AdsAdview.adsNotify(context, Constants.ADS_TYPE_INTERSTITIAL, Constants.ADS_TYPE_ERROR);
            LogUtil.e(e);
        }
    }

    public static void interstitialAdsRequest(final Context context, final Handler handler) {
        try {
            AdViewInstlManager.getInstance(context).requestAd(context, Constants.ADS_ADVIEW_KEY, new AdViewInstlListener() {

                @Override
                public void onAdClick(String s) {
                    if(handler!=null) {
                        handler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
                    }
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_INTERSTITIAL, Constants.ADS_CLICK_MSG);
                }

                @Override
                public void onAdDisplay(String s) {
                    if(handler!=null) {
                        handler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
                    }
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_INTERSTITIAL, Constants.ADS_PRESENT_MSG);
                }

                @Override
                public void onAdDismiss(String s) {
                    if(handler!=null) {
                        handler.sendEmptyMessage(Constants.ADS_DISMISS_MSG);
                    }
                }

                @Override
                public void onAdRecieved(String s) {
                    if(handler!=null) {
                        handler.sendEmptyMessage(Constants.ADS_READY_MSG);
                    }
                }

                @Override
                public void onAdFailed(String s) {
                    LogUtil.e(s);
                    if(handler!=null) {
                        handler.sendEmptyMessage(Constants.ADS_NO_MSG);
                    }
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_INTERSTITIAL, Constants.ADS_NO_MSG);
                }
            });
        } catch (Throwable e) {
            AdsAdview.adsNotify(context, Constants.ADS_TYPE_INTERSTITIAL, Constants.ADS_TYPE_ERROR);
            LogUtil.e(e);
        }
    }

    public static void interstitialAdsShow(final Context context) {
        AdViewInstlManager.getInstance(context)
                .showAd(context, Constants.ADS_ADVIEW_KEY);
    }

    public static void bannerAds(final Context context, final ViewGroup group, final Handler handler, String key) {
        try {
            View view = AdViewBannerManager.getInstance(context).getAdViewLayout(context, key);
            if (view != null) {
                ViewGroup parent = (ViewGroup) view.getParent();
                if (parent != null) {
                    parent.removeAllViews();
                }
                view.setTag(key);
            }
            AdViewBannerManager.getInstance(context).requestAd(context, key, new AdViewBannerListener() {

                @Override
                public void onAdClick(String s) {
                    if(handler!=null) {
                        handler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
                    }
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_BANNER, Constants.ADS_CLICK_MSG);
                }

                @Override
                public void onAdDisplay(String s) {
                    if(handler!=null) {
                        handler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
                    }
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_BANNER, Constants.ADS_PRESENT_MSG);
                }

                @Override
                public void onAdClose(String s) {
                    if(handler!=null) {
                        handler.sendEmptyMessage(Constants.ADS_DISMISS_MSG);
                    }
                    group.removeView(group.findViewWithTag(s));
                }

                @Override
                public void onAdFailed(String s) {
                    LogUtil.e(s);
                    if(handler!=null) {
                        handler.sendEmptyMessage(Constants.ADS_NO_MSG);
                    }
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_BANNER, Constants.ADS_NO_MSG);
                }

                @Override
                public void onAdReady(String s) {
                    if(handler!=null) {
                        handler.sendEmptyMessage(Constants.ADS_READY_MSG);
                    }
                }
            });
            group.addView(view);
            group.invalidate();
        } catch (Throwable e) {
            AdsAdview.adsNotify(context, Constants.ADS_TYPE_BANNER, Constants.ADS_TYPE_ERROR);
            LogUtil.e(e);
        }
    }

    public static void nativeAds(final Context context, final Handler handler, final NativeAdsReadyListener listener) {
        try {
            AdViewNativeManager.getInstance(context).requestAd(context, Constants.ADS_ADVIEW_KEY, 10, new AdViewNativeListener() {

                @Override
                public void onAdFailed(String s) {
                    LogUtil.e("原声广告fail：" + s);
                    if(handler!=null) {
                        handler.sendEmptyMessage(Constants.ADS_NO_MSG);
                    }
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_NATIVE, Constants.ADS_NO_MSG);
                }

                @Override
                public void onAdRecieved(String s, ArrayList arrayList) {
                    if(handler!=null) {
                        handler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
                    }
                    if(listener!=null) {
                        listener.onAdRecieved((List<NativeAdInfo>) arrayList);
                    }
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_NATIVE, Constants.ADS_PRESENT_MSG);
                    LogUtil.i("原声广告ok：" + s);
                }

                @Override
                public void onAdStatusChanged(String s, int i) {
                    if(handler!=null) {
                        handler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
                    }
                }
            }); //设置原生回调接口
        } catch (Throwable e) {
            AdsAdview.adsNotify(context, Constants.ADS_TYPE_NATIVE, Constants.ADS_TYPE_ERROR);
            LogUtil.e("原声广告fail：", e);
        }
    }

    public static void videoAdsReq(final Context context, final Handler handler) {
        //初始化之后请求视频广告，请求与展示广告要单独使用
        AdViewVideoManager.getInstance(context).requestAd(context, Constants.ADS_ADVIEW_KEY, new AdViewVideoListener() {
            @Override
            public void onAdReady(String s) {
                LogUtil.i("视频广告：onAdReady " + s);
                if(handler!=null) {
                    Message msg = Message.obtain();
                    msg.what = Constants.ADS_READY_MSG;
                    msg.arg1 = what1_video;
                    handler.sendMessage(msg);
                }
                AdsAdview.adsNotify(context, Constants.ADS_TYPE_VIDEO, Constants.ADS_READY_MSG);
            }

            @Override
            public void onAdPlayStart(String s) {
                if(handler!=null) {
                    Message msg = Message.obtain();
                    msg.what = Constants.ADS_PRESENT_MSG;
                    msg.arg1 = what1_video;
                    handler.sendMessage(msg);
                }
                AdsAdview.adsNotify(context, Constants.ADS_TYPE_VIDEO, Constants.ADS_PRESENT_MSG);
            }

            @Override
            public void onAdPlayEnd(String s, Boolean aBoolean) {
                if(handler!=null) {
                    Message msg = Message.obtain();
                    msg.what = Constants.ADS_FINISH_MSG;
                    msg.arg1 = what1_video;
                    handler.sendMessage(msg);
                }
                AdsAdview.adsNotify(context, Constants.ADS_TYPE_VIDEO, Constants.ADS_FINISH_MSG);
            }

            @Override
            public void onAdFailed(String s) {
                LogUtil.e("视频广告onAdFailed：" + s);
                if(handler!=null) {
                    Message msg = Message.obtain();
                    msg.what = Constants.ADS_NO_MSG;
                    msg.arg1 = what1_video;
                    handler.sendMessage(msg);
                }
                AdsAdview.adsNotify(context, Constants.ADS_TYPE_VIDEO, Constants.ADS_NO_MSG);
            }

            @Override
            public void onAdRecieved(String s) {
                LogUtil.e("视频广告onAdRecieved：" + s);
                if(handler!=null) {
                    Message msg = Message.obtain();
                    msg.what = Constants.ADS_READY_MSG;
                    msg.arg1 = what1_video;
                    handler.sendMessage(msg);
                }
                AdsAdview.adsNotify(context, Constants.ADS_TYPE_VIDEO, Constants.ADS_READY_MSG);
            }

            @Override
            public void onAdClose(String s) {
                if(handler!=null) {
                    Message msg = Message.obtain();
                    msg.what = Constants.ADS_CLICK_MSG;
                    msg.arg1 = what1_video;
                    handler.sendMessage(msg);
                }
                AdsAdview.adsNotify(context, Constants.ADS_TYPE_VIDEO, Constants.ADS_CLICK_MSG);
            }
        });

    }
    public static void videoAdsShow(final Context context) {
        // 设置视频回调接口 // 请求广告成功之后，调用展示广告
        AdViewVideoManager.getInstance(context).playVideo(context, Constants.ADS_ADVIEW_KEY);
    }

    public static void adsNotify(Context context, int type, int event) {
        MobAgent.onEventAds(context, type, event);
        if (event == Constants.ADS_CLICK_MSG) {
            String msg = context.getResources().getString(R.string.tab_fb_click) + Constants.ADS_SHOW_CLICK;
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            ScoreTask.start(context, Constants.ADS_SHOW_CLICK);
        }
    }

    public static String getAdsName(int type) {
        switch (type) {
            case Constants.ADS_TYPE_INIT:
                return "初始化";
            case Constants.ADS_TYPE_SPREAD:
                return "开屏广告";
            case Constants.ADS_TYPE_BANNER:
                return "插屏广告";
            case Constants.ADS_TYPE_INTERSTITIAL:
                return "弹屏广告";
            case Constants.ADS_TYPE_VIDEO:
                return "视频广告";
            case Constants.ADS_TYPE_NATIVE:
                return "本地广告";
            default:
                return "错误广告";
        }
    }

    public static String getAdsEvent(int event) {
        switch (event) {
            case Constants.ADS_CLICK_MSG:
                return "点击";
            case Constants.ADS_NO_MSG:
                return "无数据";
            case Constants.ADS_PRESENT_MSG:
                return "展示";
            case Constants.ADS_FINISH_MSG:
                return "完成";
            default:
                return "错误";
        }
    }

    public interface NativeAdsReadyListener {
        boolean onAdRecieved(List<NativeAdInfo> data);
    }
}
