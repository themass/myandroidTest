package com.sspacee.yewu.ads.base;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.view.ViewGroup;

/**
 * Created by themass on 2017/9/20.
 */

public abstract class BannerInter implements BaseAdsInter{
    public abstract void bannerAds(FragmentActivity context, ViewGroup group, Handler handler);
    public abstract void bannerExit(FragmentActivity context,ViewGroup group);
    public void noAds(Context context,Handler handler, AdsContext.AdsFrom from){
        Message msg = Message.obtain();
        msg.obj = new AdsContext.AdsMsgObj(context,AdsContext.AdsType.ADS_TYPE_BANNER, AdsContext.AdsShowStatus.ADS_NO_MSG, from);
        handler.sendMessage(msg);
    }
    public void readyAds(Context context,Handler handler,AdsContext.AdsFrom from){
        Message msg = Message.obtain();
        msg.obj = new AdsContext.AdsMsgObj(context,AdsContext.AdsType.ADS_TYPE_BANNER, AdsContext.AdsShowStatus.ADS_READY_MSG, from);
        handler.sendMessage(msg);
    }
    public void clickAds(Context context, Handler handler, AdsContext.AdsFrom from){
        Message msg = Message.obtain();
        msg.obj = new AdsContext.AdsMsgObj(context,AdsContext.AdsType.ADS_TYPE_BANNER, AdsContext.AdsShowStatus.ADS_CLICK_MSG, from);
        handler.sendMessage(msg);
    }
    public void displayAds(Context context, Handler handler, AdsContext.AdsFrom from){
        Message msg = Message.obtain();
        msg.obj = new AdsContext.AdsMsgObj(context,AdsContext.AdsType.ADS_TYPE_BANNER, AdsContext.AdsShowStatus.ADS_PRESENT_MSG, from);
        handler.sendMessage(msg);
    }
    public void closeAds(Context context, Handler handler, AdsContext.AdsFrom from){
        Message msg = Message.obtain();
        msg.obj = new AdsContext.AdsMsgObj(context,AdsContext.AdsType.ADS_TYPE_BANNER, AdsContext.AdsShowStatus.ADS_DISMISS_MSG, from);
        handler.sendMessage(msg);
    }
}
