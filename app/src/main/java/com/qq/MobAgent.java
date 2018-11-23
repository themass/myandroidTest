package com.qq;

import android.content.Context;

import com.qq.ads.base.AdsContext;
import com.umeng.analytics.MobclickAgent;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by dengt on 2016/8/18.
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
        MobclickAgent.setSessionContinueMillis(Constants.UM_INTERVAL);
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
    public static void killProcess(Context context) {
        MobclickAgent.onKillProcess(context);
    }
    public static void onEventAds(Context context, AdsContext.AdsType type, AdsContext.AdsShowStatus event) {
        Map<String, String> map = new HashMap<>();
        map.put("name", type.desc);
        map.put("event", event.desc);
        map.put("status", type.desc + " - " + event.desc);
        MobclickAgent.onEvent(context, "adsshow", map);
    }
}
