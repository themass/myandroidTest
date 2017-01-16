package com.timeline.vpn.ads.adview;

import android.content.ContentValues;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.kyview.InitConfiguration;
import com.kyview.interfaces.AdViewBannerListener;
import com.kyview.interfaces.AdViewInstlListener;
import com.kyview.interfaces.AdViewNativeListener;
import com.kyview.interfaces.AdViewSpreadListener;
import com.kyview.manager.AdViewBannerManager;
import com.kyview.manager.AdViewInstlManager;
import com.kyview.manager.AdViewNativeManager;
import com.kyview.manager.AdViewSpreadManager;
import com.kyview.natives.NativeAdInfo;
import com.timeline.vpn.R;
import com.timeline.vpn.base.MyApplication;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.MobAgent;
import com.timeline.vpn.provider.AdsInfoModel;
import com.timeline.vpn.provider.BaseContentProvider;
import com.timeline.vpn.provider.TableCreator;
import com.timeline.vpn.task.ScoreTask;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by themass on 2016/8/19.
 */
public class AdsAdview {
    public static HandlerThread adsMsgThread = new HandlerThread("ads_msg_back");
    private static InitConfiguration initConfig;

    static {
        adsMsgThread.start();
    }

    public static void initConfig() {
        InitConfiguration.Builder builder = new InitConfiguration.Builder(MyApplication.getInstance())
                .setUpdateMode(InitConfiguration.UpdateMode.EVERYTIME)   // 实时获取配置
                .setBannerCloseble(InitConfiguration.BannerSwitcher.DEFAULT)    //横幅可关闭按钮
                .setInstlDisplayType(InitConfiguration.InstlDisplayType.DIALOG_MODE)// 为默认情况,设置插屏展示模式，popupwindow模式可设置窗体外可点击
                .setInstlCloseble(InitConfiguration.InstlSwitcher.CANCLOSED);     //插屏可关闭按钮
        builder.setAdYoumiSize(InitConfiguration.AdYoumiSize.FIT_SCREEN);
        builder.setAdMobSize(InitConfiguration.AdMobSize.BANNER);
        builder.setAdInMobiSize(InitConfiguration.AdInMobiSize.INMOBI_AD_UNIT_320x50);
        builder.setAdGdtSize(InitConfiguration.AdGdtSize.BANNER);
        builder.setAdSize(InitConfiguration.AdSize.BANNER_SMART);
        if (MyApplication.isDebug) {
            builder.setRunMode(InitConfiguration.RunMode.TEST);
        } else {
            builder.setRunMode(InitConfiguration.RunMode.NORMAL);
        }
//        builder.setRunMode(InitConfiguration.RunMode.NORMAL);
        initConfig = builder.build();
    }

    public static void init(Context context) {
        AdViewBannerManager.getInstance(context).init(initConfig, Constants.adsKeySetBanner);
        AdViewInstlManager.getInstance(context).init(initConfig, Constants.adsKeySet);
        AdViewNativeManager.getInstance(context).init(initConfig, Constants.adsKeySet);
    }

    public static void launchAds(final Context context, ViewGroup group, final Handler handler) {
        try {
            if (group == null) {
                AdViewSpreadManager.getInstance(context).destroySpread(Constants.ADS_ADVIEW_KEY);
                return;
            }
            AdViewSpreadManager.getInstance(context).init(initConfig, Constants.adsKeySet);
            AdViewSpreadManager.getInstance(context).setSpreadLogo(R.drawable.ic_trans_logo);
            AdViewSpreadManager.getInstance(context).setSpreadNotifyType(AdViewSpreadManager.NOTIFY_COUNTER_NUM);
            AdViewSpreadManager.getInstance(context).request(context, Constants.ADS_ADVIEW_KEY, group, new AdViewSpreadListener() {
                @Override
                public void onAdClick(String s) {
                    handler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_SPREAD, Constants.ADS_CLICK_MSG);
                }

                @Override
                public void onAdDisplay(String s) {
                    handler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_SPREAD, Constants.ADS_PRESENT_MSG);
                }

                @Override
                public void onAdClose(String s) {
                    handler.sendEmptyMessage(Constants.ADS_DISMISS_MSG);
                }

                @Override
                public void onAdRecieved(String s) {
                    handler.sendEmptyMessage(Constants.ADS_READY_MSG);
                }

                @Override
                public void onAdFailed(String s) {
                    handler.sendEmptyMessage(Constants.ADS_NO_MSG);
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_SPREAD, Constants.ADS_NO_MSG);
                }

                @Override
                public void onAdSpreadNotifyCallback(String s, ViewGroup viewGroup, int i, int i1) {
                    Toast.makeText(context, s, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Throwable e) {
            AdsAdview.adsNotify(context, Constants.ADS_TYPE_SPREAD, Constants.ADS_TYPE_ERROR);
            LogUtil.e(e);
        }
    }

    public static void interstitialAds(final Context context, final Handler handler) {
        try {
            AdViewInstlManager.getInstance(context).requestAd(context, Constants.ADS_ADVIEW_KEY, new AdViewInstlListener() {

                @Override
                public void onAdClick(String s) {
                    handler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_INTERSTITIAL, Constants.ADS_CLICK_MSG);
                }

                @Override
                public void onAdDisplay(String s) {
                    handler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_INTERSTITIAL, Constants.ADS_PRESENT_MSG);
                }

                @Override
                public void onAdDismiss(String s) {
                    handler.sendEmptyMessage(Constants.ADS_DISMISS_MSG);
                }

                @Override
                public void onAdRecieved(String s) {
                    handler.sendEmptyMessage(Constants.ADS_READY_MSG);
                    AdViewInstlManager.getInstance(context)
                            .showAd(context, Constants.ADS_ADVIEW_KEY);
                }

                @Override
                public void onAdFailed(String s) {
                    handler.sendEmptyMessage(Constants.ADS_NO_MSG);
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_INTERSTITIAL, Constants.ADS_NO_MSG);
                }
            });
        } catch (Throwable e) {
            AdsAdview.adsNotify(context, Constants.ADS_TYPE_INTERSTITIAL, Constants.ADS_TYPE_ERROR);
            LogUtil.e(e);
        }
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
                    handler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_BANNER, Constants.ADS_CLICK_MSG);
                }

                @Override
                public void onAdDisplay(String s) {
                    handler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_BANNER, Constants.ADS_PRESENT_MSG);
                }

                @Override
                public void onAdClose(String s) {
                    handler.sendEmptyMessage(Constants.ADS_DISMISS_MSG);
                    group.removeView(group.findViewWithTag(s));
                }

                @Override
                public void onAdFailed(String s) {
                    handler.sendEmptyMessage(Constants.ADS_NO_MSG);
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_BANNER, Constants.ADS_NO_MSG);
                }

                @Override
                public void onAdReady(String s) {
                    handler.sendEmptyMessage(Constants.ADS_READY_MSG);
                }
            });
            group.addView(view);
            group.invalidate();
        } catch (Throwable e) {
            AdsAdview.adsNotify(context, Constants.ADS_TYPE_BANNER, Constants.ADS_TYPE_ERROR);
            LogUtil.e(e);
        }
    }

    public static void nativeAds(final Context context, final Handler handler, final NativeAdsAdapter.AdsAdapter adsAdapter) {
        try {
            if (adsAdapter == null || handler == null) {
                return;
            }
            AdViewNativeManager.getInstance(context).requestAd(context, Constants.ADS_ADVIEW_KEY, 80, new AdViewNativeListener() {

                @Override
                public void onAdFailed(String s) {
                    handler.sendEmptyMessage(Constants.ADS_NO_MSG);
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_NATIVE, Constants.ADS_NO_MSG);
                }

                @Override
                public void onAdRecieved(String s, ArrayList arrayList) {
                    handler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
                    adsAdapter.addData((List<NativeAdInfo>) arrayList);
                    AdsAdview.adsNotify(context, Constants.ADS_TYPE_NATIVE, Constants.ADS_PRESENT_MSG);
                }

                @Override
                public void onAdStatusChanged(String s, int i) {
                    handler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
                }
            }); //设置原生回调接口
        } catch (Throwable e) {
            AdsAdview.adsNotify(context, Constants.ADS_TYPE_NATIVE, Constants.ADS_TYPE_ERROR);
            LogUtil.e(e);
        }
    }

    public static void adsNotify(Context context, int type, int event) {
        MobAgent.onAdsEvent(context, type, event);
        if (event == Constants.ADS_CLICK_MSG) {
            String msg = context.getResources().getString(R.string.tab_fb_click) + Constants.ADS_SHOW_CLICK;
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            ScoreTask.start(context, Constants.ADS_SHOW_CLICK);
        }
    }

    public static String getAdsName(int type) {
        switch (type) {
            case Constants.ADS_TYPE_SPREAD:
                return "开屏广告";
            case Constants.ADS_TYPE_BANNER:
                return "插屏广告";
            case Constants.ADS_TYPE_INTERSTITIAL:
                return "弹屏广告";
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
            default:
                return "错误";
        }
    }

    public static class AddAdsInfoTask extends AsyncTask<String, Integer, Boolean> {
        private Context context;
        private int type;
        private int event;

        public AddAdsInfoTask(Context context, int type, int event) {
            this.context = context;
            this.type = type;
            this.event = event;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            ContentValues values = new ContentValues();
            values.put(AdsInfoModel.ADS_TYPE, type);
            values.put(AdsInfoModel.ADS_EVENT, event);
            values.put(AdsInfoModel.ADS_DATE, new Date().getTime());
            context.getContentResolver().insert(BaseContentProvider
                    .getTableUri(TableCreator.ADS_INFO), values);
            return Boolean.TRUE;
        }
    }
}
