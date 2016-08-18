package com.timeline.vpn.data;

import android.content.Context;

import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.umeng.analytics.MobclickAgent;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by gqli on 2016/8/18.
 */
public class MobAgent {
    public static void onResume(Context context){
        MobclickAgent.onPageStart(context.getClass().getSimpleName());
        MobclickAgent.onResume(context);
    }
    public static void onPause(Context context){
        MobclickAgent.onPageEnd(context.getClass().getSimpleName());
        MobclickAgent.onPause(context);
    }
    public static void onResumeForFragment(Context context){
        MobclickAgent.onResume(context);
    }
    public static void onPageEnd(Context context){
        MobclickAgent.onPause(context);
    }
    public static void onPageStart(Context context){
        MobclickAgent.onResume(context);
    }
    public static void onPauseForFragment(Context context){
        MobclickAgent.onPause(context);
    }
    public static void init(boolean isTest){
        LogUtil.i("isdebug="+isTest);
        MobclickAgent.setSessionContinueMillis(Constants.UM_INTERVAL);
        MobclickAgent.openActivityDurationTrack(false);
        MobclickAgent.enableEncrypt(true);
        MobclickAgent.setDebugMode( isTest );
    }
    public static void onProfileSignIn(String ID) {
        MobclickAgent.onProfileSignIn(ID);
    }
    public static void onProfileSignOff() {
        MobclickAgent.onProfileSignOff();
    }
    public static void onEventLocationChoose(Context context, String country){
        Map<String,String> map = new HashMap<>();
        map.put("type","location");
        map.put("name",country);
        MobclickAgent.onEvent(context, "click",map);
    }
    public static void onEventRecommond(Context context, String title){
        Map<String,String> map = new HashMap<>();
        map.put("type","recommond");
        map.put("name",title);
        MobclickAgent.onEvent(context, "click",map);
    }


}
