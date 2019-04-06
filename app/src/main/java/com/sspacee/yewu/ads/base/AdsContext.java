package com.sspacee.yewu.ads.base;

import android.app.Activity;
import android.content.Context;
import android.os.HandlerThread;
import android.widget.Toast;

import com.qq.e.comm.util.Md5Util;
import com.qq.sexfree.R;
import com.sspacee.common.util.Md5;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.ads.adview.AdviewConstant;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.AdsPopStrategy;
import com.timeline.myapp.data.UserLoginUtil;
import com.timeline.myapp.task.ScoreTask;
import com.timeline.sexfree1.ui.main.MainFragmentViewPage;

import java.util.HashSet;
import java.util.Set;

import static com.sspacee.yewu.ads.base.AdsContext.AdsShowStatus.ADS_CLICK_MSG;


/**
 * Created by dengt on 2017/9/14.
 */

public class AdsContext {
    private static int showCount = 0;
    public static Set<String> adsClick = new HashSet<>();

    static {
    }
    public static enum Categrey {
        CATEGREY_VPN1("插屏： 点击vpn页，音频，图片，小说 list 页，其他推荐 ;   banner：设置页，地区头，音频，图片，小说，视频，收藏夹列表", AdviewConstant.ADS_ADVIEW_KEY1),
        CATEGREY_VPN2("插屏： 点击vpn页，音频，图片，小说 list 页，其他推荐 ;   banner：设置页，地区头，音频，图片，小说，视频，收藏夹列表", AdviewConstant.ADS_ADVIEW_KEY2),
        CATEGREY_VPN3("插屏： 点击vpn页，音频，图片，小说 list 页，其他推荐 ;   banner：设置页，地区头，音频，图片，小说，视频，收藏夹列表", AdviewConstant.ADS_ADVIEW_KEY3);

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
        ADVIEW("adview");
        //        GDT("gdt");
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
        if(showCount++>14){
            return false;
        }
        if(UserLoginUtil.isVIP3()){
            int i = Md5.getRandom(Constants.maxRate);
            return i<=2;
        }else if(UserLoginUtil.isVIP2()){
            int i = Md5.getRandom(Constants.maxRate);
            return i<=4;
        }else if(UserLoginUtil.isVIP()){
            int i = Md5.getRandom(Constants.maxRate);
            return i<=6;
        }else{
            int i = Md5.getRandom(Constants.maxRate);
            return i<=Constants.PROBABILITY;
        }

    }
    public static void adsNotify(Context context, AdsType type, AdsShowStatus event) {
        MobAgent.onEventAds(context, type, event);
        if (event == ADS_CLICK_MSG) {
            if(!AdsPopStrategy.clickAdsClickBtn(context)){
                return;
            }
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
    public static Categrey getIndex(int num){
        int size = AdsContext.Categrey.values().length;
        return AdsContext.Categrey.values()[(num)%size];
    }

    public static void showNextAbs(Context context,AdsContext.Categrey cate){
        boolean gdt = PreferenceUtils.getPrefBoolean(context,Constants.AD_GDT_SWITCH,true);
        if(SystemUtils.isZH(context) && gdt && context instanceof  Activity){
            int index = Md5.getRandom(Constants.gdtInterlist.size());
            GdtInterManger gdtInterManger = new GdtInterManger((Activity) context,null,Constants.gdtInterlist.get(index));
            gdtInterManger.showAd();
        }else {
            int size = AdsContext.Categrey.values().length;
            AdsManager.getInstans().showInterstitialAds(context, cate, false);
        }
    }
    public static void showRand(Context context){
        if(UserLoginUtil.showAds()) {
            showRand(context,getNext());
        }
    }
    public static void showRand(Context context, AdsContext.Categrey cate){
        if (AdsContext.rateShow()) {
            boolean gdt = PreferenceUtils.getPrefBoolean(context,Constants.AD_GDT_SWITCH,true);
            if(SystemUtils.isZH(context) && gdt && context instanceof  Activity){
                int index = Md5.getRandom(Constants.gdtInterlist.size());
                GdtInterManger gdtInterManger = new GdtInterManger((Activity) context,null,Constants.gdtInterlist.get(index));
                gdtInterManger.showAd();
            }else{
                AdsManager.getInstans().showInterstitialAds(context, cate, false);
            }
        }
    }

}
