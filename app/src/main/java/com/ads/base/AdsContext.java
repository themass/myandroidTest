package com.ads.base;

import android.content.Context;
import android.os.HandlerThread;
import android.widget.Toast;

import com.ads.adview.AdviewConstant;
import com.qq.sexfree.R;

/**
 * Created by themass on 2017/9/14.
 */

public class AdsContext {
    private static int showCount = 0;
    static {
    }
    public static enum Categrey {
        CATEGREY_VPN("插屏:主页，音频，图片，小说 channel页;   banner：vip页，音频，图片，小说，视频，收藏夹列表，", AdviewConstant.ADS_ADVIEW_KEY1),
        CATEGREY_VPN1("插屏： 点击vpn页，音频，图片，小说 list 页，其他推荐 ;   banner：设置页，地区头，音频，图片，小说，视频，收藏夹列表", AdviewConstant.ADS_ADVIEW_KEY2),
        CATEGREY_VPN2("插屏： 点击积分，视频暂停；  banner：vpn状态页,国家选择列表，音频，图片，小说 channel 头页 ", AdviewConstant.ADS_ADVIEW_KEY),
        CATEGREY_VPN3("banner：文章页，音频，图片，小说，视频 头页 ", AdviewConstant.ADS_ADVIEW_KEY_BANNER);
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


}
