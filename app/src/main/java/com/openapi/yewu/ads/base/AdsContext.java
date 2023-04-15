package com.openapi.yewu.ads.base;

import android.content.Context;
import android.os.HandlerThread;

import com.openapi.common.util.DateUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.Md5;
import com.openapi.common.util.PreferenceUtils;
import com.openapi.common.util.ToastUtil;
import com.openapi.ks.free1.R;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.AdsPopStrategy;
import com.openapi.myapp.data.UserLoginUtil;
import com.openapi.myapp.task.ScoreTask;
import com.openapi.yewu.ads.adview.AdviewConstant;
import com.openapi.yewu.um.MobAgent;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static com.openapi.yewu.ads.base.AdsContext.AdsShowStatus.ADS_CLICK_MSG;


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
        CATEGREY_VPN3("插屏： 点击vpn页，音频，图片，小说 list 页，其他推荐 ;   banner：设置页，地区头，音频，图片，小说，视频，收藏夹列表", AdviewConstant.ADS_ADVIEW_KEY4);

        public String desc;
        public String key;

        Categrey(String desc, String key) {
            this.desc = desc;
            this.key = key;
        }
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
        MOBVISTA("mobvista");
        public String desc;
        AdsFrom(String desc){
            this.desc =desc;
        }
    }
    public static boolean hasClick(Context context,String key){
        if(adsClick.contains(key)){
            ToastUtil.showShort(R.string.repeated_click);
            return true;
        }
        adsClick.add(key);
        return false;
    }
    public static class AdsMsgObj{
        AdsType type;
        AdsShowStatus status;
        AdsFrom from;
        boolean score;
        Context context;
        public int count=0;
        public AdsMsgObj(Context context,AdsType type,AdsShowStatus status,AdsFrom from){
            this.type = type;
            this.status = status;
            this.from=from;
            this.context = context;
        }

        public AdsMsgObj(Context context,AdsType type,AdsShowStatus status,AdsFrom from,boolean score){
            this.type = type;
            this.status = status;
            this.from=from;
            this.score=score;
            this.context = context;
        }
        public boolean addCount(){
            count = count+1;
            LogUtil.i("count="+count);
            if(count>=2){
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
    public static boolean rateShow(){
        if(showCount++>8){
            return false;
        }
        int i = Md5.getRandom(Constants.maxRate);
        LogUtil.i("i=---"+i);
        if(UserLoginUtil.isVIP3()){
            return i<=1;
        }else if(UserLoginUtil.isVIP2()){
            return i<=2;
        }else if(UserLoginUtil.isVIP()){
            return i<=3;
        }else{
            return i<=Constants.PROBABILITY;
        }

    }
    public static void adsNotify(Context context, AdsType type, AdsShowStatus event) {
        MobAgent.onEventAds(context, type, event);
        if (event == ADS_CLICK_MSG) {
            if(!AdsPopStrategy.clickAdsClickBtn(context)){
                return;
            }
//            String msg = context.getResources().getString(R.string.tab_fb_click) + Constants.ADS_SHOW_CLICK;
//            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            ScoreTask.start(context, Constants.ADS_SHOW_CLICK);
        }
    }
    public static int index=0;
    public static Categrey getNext(){
        int size = AdsContext.Categrey.values().length;
        return AdsContext.Categrey.values()[(index++)%2];
    }
    public static Categrey getIndex(int num){
        int size = AdsContext.Categrey.values().length;
        return AdsContext.Categrey.values()[(num)%size];
    }
    public static void showRand(Context context, AdsContext.Categrey cate){
        if(AdsContext.rateShow()) {
            AdsManager.getInstans().showInterstitialAds(context, AdsContext.Categrey.CATEGREY_VPN3, false, AdsContext.AdsFrom.MOBVISTA, 1);
        }
    }
    public static void showRand2(Context context, AdsContext.Categrey cate){
        if(AdsContext.rateShow()) {
            AdsManager.getInstans().showInterstitialAds(context, AdsContext.Categrey.CATEGREY_VPN3, false, AdsContext.AdsFrom.MOBVISTA,1);
        }
    }
    public static void vpnClick(Context context){
        String key =Constants.CLICK_KEY+ DateUtils.format(new Date(),DateUtils.DATE_MM_FORMAT);
        int vpnCount = PreferenceUtils.getPrefInt(context,key,0);
        LogUtil.i("key="+key+"   vvvCount="+vpnCount);
        vpnCount=vpnCount>100?100:++vpnCount;
        PreferenceUtils.setPrefInt(context,key,vpnCount);
    }
    public static int getVpnClick(Context context){
        String key =Constants.CLICK_KEY+DateUtils.format(new Date(),DateUtils.DATE_MM_FORMAT);
        return  PreferenceUtils.getPrefInt(context,key,0);
    }
}
