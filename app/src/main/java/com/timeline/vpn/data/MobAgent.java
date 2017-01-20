package com.timeline.vpn.data;

import android.content.Context;

import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.base.MyApplication;
import com.timeline.vpn.constant.Constants;
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

    public static void onPageEnd(Context context) {
        MobclickAgent.onPause(context);
    }

    public static void onPageStart(Context context) {
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

    public static void onEventRecommond(Context context, String title) {
        Map<String, String> map = new HashMap<>();
        map.put("name", title);
        MobclickAgent.onEvent(context, "recommond", map);
    }

    public static void onAdsEvent(Context context, int type, int event) {
        Map<String, String> map = new HashMap<>();
        map.put("name", AdsAdview.getAdsName(type));
        map.put("event", AdsAdview.getAdsEvent(event));
        map.put("status", AdsAdview.getAdsName(type)+" - "+AdsAdview.getAdsEvent(event));
        MobclickAgent.onEvent(context, "adsshow", map);
    }

}
