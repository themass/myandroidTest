package com.sspacee.yewu.ads.base;

import android.content.Context;
import android.os.HandlerThread;
import android.widget.Toast;

import com.qq.sexfree.R;
import com.sspacee.common.util.Md5;
import com.sspacee.yewu.ads.adview.AdviewConstant;
import com.sspacee.yewu.um.MobAgent;

import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.UserLoginUtil;
import com.timeline.myapp.task.ScoreTask;

import static com.sspacee.yewu.ads.base.AdsContext.AdsShowStatus.ADS_CLICK_MSG;

/**
 * Created by themass on 2017/9/14.
 */

public class AdsContext {
    public static enum Categrey{
        CATEGREY_VPN1("插屏:主页;   banner：主页，其他", AdviewConstant.ADS_ADVIEW_KEY1),
        CATEGREY_VPN2("插屏：vpn页 ;   banner：vip页，文字，图片，声音",AdviewConstant.ADS_ADVIEW_KEY2),
        CATEGREY_VPN3("插屏：点击积分，图片，文字；  banner：地区尾，文字，图片，声音 ",AdviewConstant.ADS_ADVIEW_KEY3);
        public String desc;
        public String key;
        Categrey(String desc,String key){
            this.desc =desc;
            this.key =key;
        }
//        public static void randomShow(Context context){
//            int size = AdsContext.Categrey.values().length;
//            AdsManager.getInstans().showInterstitialAds(context,AdsContext.Categrey.values()[Md5.getRandom(size)] , true);
//        }
//        public static Categrey random(){
//            int size = AdsContext.Categrey.values().length;
//            return AdsContext.Categrey.values()[Md5.getRandom(size)];
//        }
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
        ADVIEW("adview");
//        GDT("gdt");
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
        public AdsMsgObj(Context context,AdsType type,AdsShowStatus status,AdsFrom from){
            this.type = type;
            this.status = status;
            this.from=from;
        }
        public AdsMsgObj(Context context,AdsType type,AdsShowStatus status,AdsFrom from,boolean score){
            this.type = type;
            this.status = status;
            this.from=from;
            this.score=score;
        }
    }

    public static HandlerThread adsMsgThread = new HandlerThread("ads_msg_back");
    static {
        adsMsgThread.start();
    }
    // 3/5
    public static boolean rateShow(){
        if(UserLoginUtil.isVIP3()){
            int i = Md5.getRandom(Constants.maxRate);
            return i<=6;
        }else if(UserLoginUtil.isVIP2()){
            int i = Md5.getRandom(Constants.maxRate);
            return i<=7;
        }else if(UserLoginUtil.isVIP()){
            int i = Md5.getRandom(Constants.maxRate);
            return i<=8;
        }else{
            int i = Md5.getRandom(Constants.maxRate);
            return i<=9;
        }

    }
    public static boolean rateSmallShow(){
        if(UserLoginUtil.isVIP2()){
            int i = Md5.getRandom(Constants.maxRate);
            return i<=2;
        }else{
            int i = Md5.getRandom(Constants.maxRate);
            return i<=3;
        }
    }
    public static void adsNotify(Context context, AdsType type, AdsShowStatus event) {
        MobAgent.onEventAds(context, type, event);
        if (event == ADS_CLICK_MSG) {
            String msg = context.getResources().getString(R.string.tab_fb_click) + Constants.ADS_SHOW_CLICK;
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            ScoreTask.start(context, Constants.ADS_SHOW_CLICK);
        }
    }
    public static int index=0;
    public static Categrey getNext(){
        int size = AdsContext.Categrey.values().length;
        return AdsContext.Categrey.values()[(index++)%2];
    }
    public static void showNext(Context context){
        int size = AdsContext.Categrey.values().length;
        AdsManager.getInstans().showInterstitialAds(context, AdsContext.Categrey.values()[(index++) % size], false);
    }
    public static void showNextAbs(Context context){
        int size = AdsContext.Categrey.values().length;
        AdsManager.getInstans().showInterstitialAds(context, AdsContext.Categrey.values()[(index++) % size], false);
    }
    public static void showRand(Context context){
        if(UserLoginUtil.showAds()) {
            if (AdsContext.rateShow()) {
                showNext(context);
            }
        }
    }
    public static void showRand(Context context,AdsContext.Categrey cate){
        if(UserLoginUtil.showAds()) {
            if (AdsContext.rateShow()) {
                AdsManager.getInstans().showInterstitialAds(context, cate, false);
            }
        }
    }

}
