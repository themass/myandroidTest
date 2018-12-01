package com.qq.yewu.ads.base;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

/**
 * Created by dengt on 2017/9/21.
 */

public abstract class AdsEventAdaptor {
    public void noAds(Context context, Handler handler, com.qq.yewu.ads.base.AdsContext.AdsFrom from){
        Message msg = Message.obtain();
        msg.obj = new com.qq.yewu.ads.base.AdsContext.AdsMsgObj(context,getAdsType(), com.qq.yewu.ads.base.AdsContext.AdsShowStatus.ADS_NO_MSG, from);
        handler.sendMessage(msg);
    }
    public void readyAds(Context context, Handler handler, com.qq.yewu.ads.base.AdsContext.AdsFrom from){
        Message msg = Message.obtain();
        msg.obj = new com.qq.yewu.ads.base.AdsContext.AdsMsgObj(context,getAdsType(), com.qq.yewu.ads.base.AdsContext.AdsShowStatus.ADS_READY_MSG, from);
        handler.sendMessage(msg);
    }
    public void clickAds(Context context, Handler handler, com.qq.yewu.ads.base.AdsContext.AdsFrom from){
        Message msg = Message.obtain();
        msg.obj = new com.qq.yewu.ads.base.AdsContext.AdsMsgObj(context,getAdsType(), com.qq.yewu.ads.base.AdsContext.AdsShowStatus.ADS_CLICK_MSG, from);
        handler.sendMessage(msg);
    }
    public void displayAds(Context context, Handler handler, com.qq.yewu.ads.base.AdsContext.AdsFrom from){
        Message msg = Message.obtain();
        msg.obj = new com.qq.yewu.ads.base.AdsContext.AdsMsgObj(context,getAdsType(), com.qq.yewu.ads.base.AdsContext.AdsShowStatus.ADS_PRESENT_MSG, from);
        handler.sendMessage(msg);
    }
    public void closeAds(Context context, Handler handler, com.qq.yewu.ads.base.AdsContext.AdsFrom from){
        Message msg = Message.obtain();
        msg.obj = new com.qq.yewu.ads.base.AdsContext.AdsMsgObj(context,getAdsType(), com.qq.yewu.ads.base.AdsContext.AdsShowStatus.ADS_DISMISS_MSG, from);
        handler.sendMessage(msg);
    }

    protected abstract com.qq.yewu.ads.base.AdsContext.AdsType getAdsType();
}
