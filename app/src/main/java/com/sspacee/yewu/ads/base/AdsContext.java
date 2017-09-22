package com.sspacee.yewu.ads.base;

import android.content.Context;
import android.os.HandlerThread;
import android.widget.Toast;

import com.sspacee.common.util.Md5;
import com.sspacee.yewu.ads.adview.AdviewConstant;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.task.ScoreTask;

import static com.sspacee.yewu.ads.base.AdsContext.AdsShowStatus.ADS_CLICK_MSG;

/**
 * Created by themass on 2017/9/14.
 */

public class AdsContext {
    static {
    }
    public static enum Categrey{
        CATEGREY_1("插屏:主页，3个channel;banner：国家选择，vip，3个channel", AdviewConstant.ADS_ADVIEW_KEY1),
        CATEGREY_2("插屏： 3个list，其他推荐;banner：3个list，文章页",AdviewConstant.ADS_ADVIEW_KEY2),
        CATEGREY_3("插屏： 暂停,积分,banner：列表信息流",AdviewConstant.ADS_ADVIEW_KEY_BANNER);
        public String desc;
        public String key;
        Categrey(String desc,String key){
            this.desc =desc;
            this.key =key;
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
    // 3/5
    public static boolean rateShow(){
        int i = Md5.getRandom(Constants.maxRate);
        return i<=7;
    }
    public static void adsNotify(Context context, AdsType type, AdsShowStatus event) {
        MobAgent.onEventAds(context, type, event);
        if (event == ADS_CLICK_MSG) {
            String msg = context.getResources().getString(R.string.tab_fb_click) + Constants.ADS_SHOW_CLICK;
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            ScoreTask.start(context, Constants.ADS_SHOW_CLICK);
        }
    }


}
