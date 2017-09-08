package com.sspacee.yewu.um;

import android.content.Context;

import com.sspacee.common.CommonConstants;
import com.sspacee.yewu.ads.adview.AdsAdview;
import com.timeline.vpn.base.MyApplication;
import com.umeng.analytics.MobclickAgent;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by themass on 2016/8/18.
 */
public class MobAgent {
    public static void onResume(Context context) {
        MobclickAgent.onPageStart(context.getClass().getSimpleName());
        MobclickAgent.onResume(context);
    }

    public static void onPause(Context context) {
        MobclickAgent.onPageEnd(context.getClass().getSimpleName());
        MobclickAgent.onPause(context);
    }

    public static void onResumeForFragment(Context context, String fragment) {
        MobclickAgent.onPageStart(fragment);
    }

    public static void onPauseForFragmentActiviy(Context context) {
        MobclickAgent.onPause(context);
    }

    public static void onResumeForFragmentActiviy(Context context) {
        MobclickAgent.onResume(context);
    }

    public static void onPauseForFragment(Context context, String fragment) {
        MobclickAgent.onPageEnd(fragment);
    }

    public static void init(Context context) {
        MobclickAgent.setSessionContinueMillis(CommonConstants.UM_INTERVAL);
        MobclickAgent.openActivityDurationTrack(false);
        MobclickAgent.enableEncrypt(true);
        MobclickAgent.setScenarioType(context, MobclickAgent.EScenarioType.E_UM_NORMAL);
        MobclickAgent.setDebugMode(MyApplication.isDebug);
    }

    public static void onProfileSignIn(String ID) {
        MobclickAgent.onProfileSignIn(ID);
    }

    public static void onProfileSignOff() {
        MobclickAgent.onProfileSignOff();
    }

    public static void onEventLocationChoose(Context context, String country) {
        Map<String, String> map = new HashMap<>();
        map.put("name", country);
        MobclickAgent.onEvent(context, "location", map);
    }

    public static void onEventMenu(Context context, String name) {
        Map<String, String> map = new HashMap<>();
        map.put("name", name);
        MobclickAgent.onEvent(context, "menu", map);
    }

    public static void onEventRecommond(Context context, String title) {
        Map<String, String> map = new HashMap<>();
        map.put("name", title);
        MobclickAgent.onEvent(context, "recommond", map);
    }
    public static void onEventRecommondChannel(Context context, String title) {
        Map<String, String> map = new HashMap<>();
        map.put("name", title);
        MobclickAgent.onEvent(context, "recommond_channel", map);
    }
    public static void onEventAds(Context context, int type, int event) {
        Map<String, String> map = new HashMap<>();
        map.put("name", AdsAdview.getAdsName(type));
        map.put("event", AdsAdview.getAdsEvent(event));
        map.put("status", AdsAdview.getAdsName(type) + " - " + AdsAdview.getAdsEvent(event));
        MobclickAgent.onEvent(context, "adsshow", map);
    }

    public static void killProcess(Context context) {
        MobclickAgent.onKillProcess(context);
    }

}
