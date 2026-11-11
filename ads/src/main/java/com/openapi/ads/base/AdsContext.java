package com.openapi.ads.base;

import static com.openapi.ads.base.AdsContext.AdsShowStatus.ADS_CLICK_MSG;

import android.content.Context;
import android.os.HandlerThread;

import com.openapi.ads.R;
import com.openapi.ads.AdsPopStrategy;
import com.openapi.ads.AdsIds;
import com.openapi.ads.adview.AdviewConstant;
import com.openapi.ads.analytics.MobAgent;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.lang.Md5;
import com.openapi.common.util.android.ToastUtil;

import java.util.HashSet;
import java.util.Set;


/**
 * Created by dengt on 2017/9/14.
 */

public class AdsContext {
    private static int showCount = 0;
    public static Set<String> adsClick = new HashSet<>();

    static {
    }

    public static enum Categrey {
        CATEGREY_VPN("插屏:主页，音频，图片，小说 channel页;   banner：vip页，音频，图片，小说，视频，收藏夹列表，", AdviewConstant.ADS_ADVIEW_KEY1),
        CATEGREY_VPN1("插屏： 点击vpn页，音频，图片，小说 list 页，其他推荐 ;   banner：设置页，地区头，音频，图片，小说，视频，收藏夹列表", AdviewConstant.ADS_ADVIEW_KEY2),
        CATEGREY_VPN2("插屏： 点击vpn页，音频，图片，小说 list 页，其他推荐 ;   banner：设置页，地区头，音频，图片，小说，视频，收藏夹列表", AdviewConstant.ADS_ADVIEW_KEY),
        CATEGREY_VPN3("插屏： 点击vpn页，音频，图片，小说 list 页，其他推荐 ;   banner：设置页，地区头，音频，图片，小说，视频，收藏夹列表", AdviewConstant.ADS_ADVIEW_KEY);

        public String desc;
        public String key;

        Categrey(String desc, String key) {
            this.desc = desc;
            this.key = key;
        }
    }

    public static enum AdsType {
        ADS_TYPE_INIT("初始化"),
        ADS_TYPE_BANNER("BANNER广告"),
        ADS_TYPE_SPREAD("开屏广告"),
        ADS_TYPE_INTERSTITIAL("插屏广告"),
        ADS_TYPE_NATIVE("本地广告"),
        ADS_TYPE_VIDEO("视频广告"),
        ADS_TYPE_NATIVE_VEDIO("本地视频广告"),
        ADS_TYPE_NATIVE_INTERSTITIAL("本地插屏广告"),
        ADS_TYPE_OFFER("积分墙广告"),
        ;
        public String desc;

        AdsType(String desc) {
            this.desc = desc;
        }
    }

    public static enum AdsShowStatus {
        ADS_NO_MSG("无广告"),
        ADS_DISMISS_MSG("广告关闭"),
        ADS_PRESENT_MSG("有广告"),
        ADS_READY_MSG("广告准备好"),
        ADS_CLICK_MSG("点击"),
        ADS_FINISH_MSG("广告完毕");
        public String desc;

        AdsShowStatus(String desc) {
            this.desc = desc;
        }
    }

    public static enum AdsFrom {
        ADVIEW("adview"),
        ADMOB("admob");
        public String desc;

        AdsFrom(String desc) {
            this.desc = desc;
        }
    }

    public static boolean hasClick(Context context, String key) {
        if (adsClick.contains(key)) {
            ToastUtil.showShort(R.string.repeated_click);
            return true;
        }
        adsClick.add(key);
        return false;
    }

    public static class AdsMsgObj {
        AdsType type;
        AdsShowStatus status;
        AdsFrom from;
        boolean score;
        Context context;
        public int count = 0;

        public AdsMsgObj(Context context, AdsType type, AdsShowStatus status, AdsFrom from) {
            this.type = type;
            this.status = status;
            this.from = from;
            this.context = context;
        }

        public AdsMsgObj(Context context, AdsType type, AdsShowStatus status, AdsFrom from, boolean score) {
            this.type = type;
            this.status = status;
            this.from = from;
            this.score = score;
            this.context = context;
        }

        public boolean addCount() {
            count = count + 1;
            LogUtil.i("count=" + count);
            if (count >= 2) {
                return false;
            }
            return true;
        }

    }

    public static HandlerThread adsMsgThread = new HandlerThread("ads_msg_back");

    static {
        adsMsgThread.start();
    }

    // 3/5
    public static boolean rateShow() {
        if (showCount++ > 8) {
            return false;
        }
        int i = Md5.getRandom(AdsIds.maxRate);
        return i <= AdsIds.PROBABILITY;

    }

    public static void adsNotify(Context context, AdsType type, AdsShowStatus event) {
        MobAgent.onEventAds(context, type, event);
        if (event == ADS_CLICK_MSG) {
            if (!AdsPopStrategy.clickAdsClickBtn(context)) {
                return;
            }
//            String msg = context.getResources().getString(R.string.tab_fb_click) + AdsIds.ADS_SHOW_CLICK;
//            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
//            ScoreTask.start(context, AdsIds.ADS_SHOW_CLICK);
        }
    }

    public static int index = 0;

    public static Categrey getNext() {
        return Categrey.values()[(index++) % 2];
    }

    public static void showRand(Context context, Categrey cate) {
        if (AdsContext.rateShow()) {
            AdsManager.getInstans().showInterstitialAds(context, Categrey.CATEGREY_VPN, false, AdsFrom.ADMOB, 1);
        }
    }
    public static void showRand(Context context, AdsFrom from) {
        if (AdsContext.rateShow()) {
            AdsManager.getInstans().showInterstitialAds(context, Categrey.CATEGREY_VPN, false, from, 1);
        }
    }
    public static void showRand(Context context) {
        if (AdsContext.rateShow()) {
            AdsManager.getInstans().showInterstitialAds(context, Categrey.CATEGREY_VPN, false, AdsFrom.ADMOB, 1);
        }
    }
    public static void showNext(Context context) {
        AdsManager.getInstans().showInterstitialAds(context, AdsContext.getNext(), false, AdsFrom.ADMOB, 1);
    }
}
