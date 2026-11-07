package com.openapi.core.ads.yewu.um;

import android.content.Context;

import com.openapi.core.ads.yewu.ads.base.AdsContext;
import com.openapi.core.util.LogUtil;

/**
 * Created by dengt on 2016/8/18.
 */
public class MobAgent {
    public static void onResume(Context context) {
    }

    public static void onPause(Context context) {
    }

    public static void onResumeForFragment(Context context, String fragment) {
    }

    public static void onPauseForFragmentActiviy(Context context) {
    }

    public static void onResumeForFragmentActiviy(Context context) {
    }

    public static void onPauseForFragment(Context context, String fragment) {
    }

    public static void init(Context context) {
    }

    public static void onProfileSignIn(String ID) {

    }

    public static void onProfileSignOff() {

    }

    public static void onEventLocationChoose(Context context, String country) {
    }

    public static void onEventMenu(Context context, String name) {
        LogUtil.i("点击:"+name);
    }

    public static void onEventRecommond(Context context, String title) {
    }

    public static void onEventRecommondChannel(Context context, String title) {
        LogUtil.i("点击:"+title);
    }

    public static void onEventAds(Context context, AdsContext.AdsType type, AdsContext.AdsShowStatus event) {
    }

    public static void killProcess(Context context) {

    }

}
