package com.timeline.vpn.ads.adview;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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
 * Created by gqli on 2016/8/19.
 */
public class AdsAdview {
    public static HandlerThread adsMsgThread = new HandlerThread("ads_msg_back");

    static {
        adsMsgThread.start();
    }

    public static void init(Context context) {
        AdViewBannerManager.getInstance(context).init(MyApplication.getInitConfig(context), Constants.adsKeySetBanner);
        AdViewInstlManager.getInstance(context).init(MyApplication.getInitConfig(context), Constants.adsKeySet);
        AdViewNativeManager.getInstance(context).init(MyApplication.getInitConfig(context), Constants.adsKeySet);
        AdViewSpreadManager.getInstance(context).init(MyApplication.getInitConfig(context), Constants.adsKeySet);
    }

    public static void launchAds(final Context context, ViewGroup group, final Handler handler) {
        AdViewSpreadManager.getInstance(context)
                .setSpreadLogo(
                        new BitmapDrawable(context.getResources(), BitmapFactory
                                .decodeResource(context.getResources(),
                                        R.drawable.ic_lauch_bottom)));
        AdViewSpreadManager.getInstance(context).request(context, Constants.ADS_ADVIEW_KEY, group, new AdViewSpreadListener() {
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
    }

    public static void interstitialAds(final Context context, final Handler handler) {
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
    }

    public static void bannerAds(final Context context, final ViewGroup group, final Handler handler, String key) {
        View view = AdViewBannerManager.getInstance(context).getAdViewLayout(context, key);
        if (view != null) {
            group.removeView(view);
        }
        AdViewBannerManager.getInstance(context).requestAd(context, key, new AdViewBannerListener() {

            @Override
            public void onAdClick(String s) {
                handler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
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
        view.setTag(key);
        group.addView(view);
        group.invalidate();
    }

    public static void nativeAds(final Context context, final Handler handler, final NativeAdsAdapter.AdsAdapter adsAdapter) {
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
    }

    public static void adsNotify(Context context, int type, int event) {
        MobAgent.onAdsEvent(context, type, event);
        if (event == Constants.ADS_CLICK_MSG) {
            String msg = context.getResources().getString(R.string.tab_fb_click) + Constants.ADS_SHOW_CLICK;
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            ScoreTask.start(context, Constants.ADS_SHOW_CLICK);
        }
    }
    public static class AddAdsInfoTask extends AsyncTask<String,Integer,Boolean> {
        private Context context;
        private int type;
        private int event;
        public AddAdsInfoTask(Context context, int type, int event){
            this.context = context;
            this.type = type;
            this.event = event;
        }
        @Override
        protected Boolean doInBackground(String ... params){
            ContentValues values = new ContentValues();
            values.put(AdsInfoModel.ADS_TYPE, type);
            values.put(AdsInfoModel.ADS_EVENT, event);
            values.put(AdsInfoModel.ADS_DATE, new Date().getTime());
            context.getContentResolver().insert(BaseContentProvider
                        .getTableUri(TableCreator.ADS_INFO), values);
            return Boolean.TRUE;
        }
    }
    public static String getAdsName(int type){
        switch (type) {
            case Constants.ADS_TYPE_SPREAD: return "开屏广告";
            case Constants.ADS_TYPE_BANNER: return "插屏广告";
            case Constants.ADS_TYPE_INTERSTITIAL: return "弹屏广告";
            case Constants.ADS_TYPE_NATIVE:return "本地广告";
            default: return "错误广告";
        }
    }
    public static String getAdsEvent(int event){
        switch (event) {
            case Constants.ADS_CLICK_MSG: return "点击";
            case Constants.ADS_NO_MSG: return "无数据";
            case Constants.ADS_PRESENT_MSG: return "展示";
            default: return "错误";
        }
    }
}
