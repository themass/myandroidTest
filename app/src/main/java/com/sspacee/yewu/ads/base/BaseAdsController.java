package com.sspacee.yewu.ads.base;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.Md5;
import com.sspacee.yewu.ads.adview.AdsAdview;
import com.sspacee.yewu.ads.youmi.YoumiAds;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.R;
import com.timeline.vpn.base.MyApplication;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.data.config.HindBannerEvent;
import com.timeline.vpn.data.config.LaunchAdsNext;
import com.timeline.vpn.task.ScoreTask;

import java.util.HashMap;
import java.util.Map;

import static com.sspacee.yewu.ads.base.BaseAdsController.AdsShowStatus.ADS_CLICK_MSG;

/**
 * Created by themass on 2017/9/14.
 */

public class BaseAdsController {
    static AdsAdview adview = new AdsAdview();
    static YoumiAds youmiAds = new YoumiAds();
    static Map<AdsFrom,AdsControllerInte> map = new HashMap<>();
    static {
        map.put(AdsFrom.ADVIEW,adview);
        map.put(AdsFrom.YOUMI,youmiAds);
    }
    public static enum  AdsType {
        ADS_TYPE_INIT ("初始化"),
        ADS_TYPE_BANNER("BANNER广告"),
        ADS_TYPE_SPREAD("开屏广告"),
        ADS_TYPE_INTERSTITIAL("插屏广告"),
        ADS_TYPE_NATIVE("本地广告"),
        ADS_TYPE_VIDEO("视频广告"),
        ADS_TYPE_NATIVE_VEDIO("本地视频广告"),
        ADS_TYPE_NATIVE_INTERSTITIAL("本地插屏广告"),
        ADS_TYPE_OFFER("积分墙广告"),;
        public String desc;
        AdsType(String desc){
            this.desc =desc;
        }
    }
    public static enum AdsShowStatus{
        ADS_NO_MSG("无广告"),
        ADS_DISMISS_MSG("广告关闭"),
        ADS_PRESENT_MSG("有广告"),
        ADS_READY_MSG("广告准备好"),
        ADS_CLICK_MSG ("点击"),
        ADS_FINISH_MSG("广告完毕");
        public String desc;
        AdsShowStatus(String desc){
            this.desc =desc;
        }
    }
    public static enum AdsFrom{
        ADVIEW("adview"),
        YOUMI("youmi");
        public String desc;
        AdsFrom(String desc){
            this.desc =desc;
        }
    }
    public static class AdsMsgObj{
        AdsType type;
        AdsShowStatus status;
        AdsFrom from;
        boolean score;
        public AdsMsgObj(AdsType type,AdsShowStatus status,AdsFrom from){
            this.type = type;
            this.status = status;
            this.from=from;
        }
        public AdsMsgObj(AdsType type,AdsShowStatus status,AdsFrom from,boolean score){
            this.type = type;
            this.status = status;
            this.from=from;
            this.score=score;
        }
    }
    private static  Handler mHandle = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            AdsMsgObj obj = (AdsMsgObj)msg.obj;
            LogUtil.i("Adstype = "+obj.type.name()+"; status="+obj.status.name()+"; from="+obj.from.name());
            adsNotify(MyApplication.getInstance(),obj.type,obj.status);
            if(obj.type==AdsType.ADS_TYPE_SPREAD && obj.status==AdsShowStatus.ADS_NO_MSG){
                EventBusUtil.getEventBus().post(new LaunchAdsNext(obj.from));
            }
            if(obj.type==AdsType.ADS_TYPE_INTERSTITIAL && obj.status==AdsShowStatus.ADS_NO_MSG && obj.from==AdsFrom.ADVIEW){
                interstitialAds(MyApplication.getInstance(),AdsFrom.YOUMI);
            }
            if(obj.type==AdsType.ADS_TYPE_INTERSTITIAL && obj.status==AdsShowStatus.ADS_READY_MSG &&obj.score){
                String text = MyApplication.getInstance().getResources().getString(R.string.tab_fb_click) + Constants.ADS_SHOW_SCORE;
                Toast.makeText(MyApplication.getInstance(), text, Toast.LENGTH_SHORT).show();
                ScoreTask.start(MyApplication.getInstance(), Constants.ADS_SHOW_SCORE);
            }
            if(obj.type==AdsType.ADS_TYPE_INTERSTITIAL && obj.status==AdsShowStatus.ADS_NO_MSG && obj.from==AdsFrom.YOUMI){
                if (!UserLoginUtil.isVIP2()&&BaseAdsController.rateShow())
                    videoAds(MyApplication.getInstance());
            }
            if(obj.type==AdsType.ADS_TYPE_BANNER && obj.status==AdsShowStatus.ADS_NO_MSG){
                EventBusUtil.getEventBus().post(new HindBannerEvent());
            }
            super.handleMessage(msg);
        }
    };
    public static HandlerThread adsMsgThread = new HandlerThread("ads_msg_back");
    static {
        adsMsgThread.start();
    }
    public static void init(Context context){
        adview.init(context);
        youmiAds.init(context);
    }
    public static void bannerAds( Context context,  ViewGroup group) {
        adview.bannerAds(context,group,mHandle);
    }
    public static void bannerAds( Context context,  ViewGroup group,AdsFrom type) {
        map.get(type).bannerAds(context,group,mHandle);
    }
    public static void exitBanner(Context context,ViewGroup view){
        youmiAds.bannerExit(context,view);
    }
    public static void exitApp(Context context){
        youmiAds.exitApp(context);
        adview.exitApp(context);
    }
    public static void lanchExit(Context context){
        adview.lanchExit(context);
        youmiAds.lanchExit(context);
    }
    public static void launchAds( Context mContext, RelativeLayout group, RelativeLayout skipView) {
        adview.launchAds(mContext,group,skipView,mHandle);
    }
    public static void launchAds( Context mContext, RelativeLayout group, RelativeLayout skipView,AdsFrom type) {
        map.get(type).launchAds(mContext,group,skipView,mHandle);
    }
    public static void interstitialAds( Context context) {
        adview.interstitialAds(context,mHandle,false);
    }
    public static void interstitialAds( Context context,AdsFrom type) {
        map.get(type).interstitialAds(context,mHandle,false);
    }
    public static void interstitialAdsScore( Context context) {
        adview.interstitialAds(context,mHandle,true);
    }
    public static void interstitialAdsScore( Context context,AdsFrom type) {
        map.get(type).interstitialAds(context,mHandle,true);
    }
    public static View nativeIntersAds( Context context) {
       return youmiAds.nativeIntersAds(context,mHandle);
    }
    public static void nativeVideoAds( Context context,ViewGroup group) {
        youmiAds.nativeVideoAds(context,mHandle,group);
    }
    public static void nativeAds( Context context,  NativeAdsReadyListener listener) {
        adview.nativeAds(context,mHandle,listener);
    }
    public static void videoAds(Context context) {
        youmiAds.videoAds(context,mHandle);
    }

    public static void onStart(Context context) {
        youmiAds.onStart(context);
    }
    public static void onResume(Context context) {
        youmiAds.onResume(context);
    }
    public static void onPause(Context context) {
        youmiAds.onPause(context);
    }
    public static void onStop(Context context){
        youmiAds.onStop(context);
    }
    public static void onDestroy(Context context) {
        youmiAds.onDestroy(context);
    }
    public static boolean rateShow(){
        return Md5.getRandom(Constants.maxRate)==1;
    }
    public static void adsNotify(Context context, AdsType type, AdsShowStatus event) {
        MobAgent.onEventAds(context, type, event);
        if (event == ADS_CLICK_MSG) {
            String msg = context.getResources().getString(R.string.tab_fb_click) + Constants.ADS_SHOW_CLICK;
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            ScoreTask.start(context, Constants.ADS_SHOW_CLICK);
        }
    }

    public static void offerAds(Context context,AdsFrom type){
        map.get(type).offerAds(context,mHandle);
    }

}
