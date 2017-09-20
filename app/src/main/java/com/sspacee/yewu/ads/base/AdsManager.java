package com.sspacee.yewu.ads.base;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.StringUtils;
import com.sspacee.yewu.ads.adview.AdviewAdsManager;
import com.sspacee.yewu.ads.adview.BannerAdviewAds;
import com.sspacee.yewu.ads.adview.SplashAdviewAds;
import com.sspacee.yewu.ads.config.AdsShowStrategyConfig;
import com.sspacee.yewu.ads.config.BannerAdsNext;
import com.sspacee.yewu.ads.config.LaunchAdsNext;
import com.sspacee.yewu.ads.gdt.BannerGdtAds;
import com.sspacee.yewu.ads.gdt.GdtAdsManger;
import com.sspacee.yewu.ads.gdt.SpashGdtAds;
import com.timeline.vpn.base.MyApplication;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by themass on 2017/9/20.
 */

public class AdsManager {
    static AdsManager manager = new AdsManager();
    public AdsShowStrategyConfig config = new AdsShowStrategyConfig();
    private AdsManager(){}
    private Map<AdsContext.AdsFrom,SplashAdsInter> splashMap = new HashMap<>();

    private Map<String,BannerInter> bannerDescMap = new HashMap<>();
    public static AdsManager getInstans(){
        return  manager;
    }
    public void init(Context context){
        LogUtil.i("AdsManager initok");
        AdviewAdsManager.init(context);
        GdtAdsManger.init(context);
        bannerDescMap.put(AdsContext.AdsFrom.ADVIEW.desc,new BannerAdviewAds());
        bannerDescMap.put(AdsContext.AdsFrom.GDT.desc,new BannerGdtAds());

        splashMap.put(AdsContext.AdsFrom.ADVIEW,new SplashAdviewAds());
        splashMap.put(AdsContext.AdsFrom.GDT,new SpashGdtAds());
    }
    private Handler mHandle = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            AdsContext.AdsMsgObj obj = (AdsContext.AdsMsgObj)msg.obj;
            LogUtil.i("Adstype = "+obj.type.name()+"; status="+obj.status.name()+"; from="+obj.from.name());
            AdsContext.adsNotify(MyApplication.getInstance(),obj.type,obj.status);
            if(obj.type== AdsContext.AdsType.ADS_TYPE_BANNER && obj.status== AdsContext.AdsShowStatus.ADS_NO_MSG){
                EventBusUtil.getEventBus().post(new BannerAdsNext(obj.from));
            }
            if(obj.type== AdsContext.AdsType.ADS_TYPE_SPREAD && obj.status== AdsContext.AdsShowStatus.ADS_NO_MSG){
                EventBusUtil.getEventBus().post(new LaunchAdsNext(obj.from));
            }
            super.handleMessage(msg);
        }
    };
    public void showBannerAds(FragmentActivity context, ViewGroup group){
        String type= config.getNextBanner();
        if(StringUtils.hasText(type)){
            bannerDescMap.get(type).bannerAds(context,group,mHandle);
        }
    }
    public void exitBannerAds(FragmentActivity context, ViewGroup group,String type){
        bannerDescMap.get(type).bannerExit(context,group);
    }
    public void showSplashAds(FragmentActivity context, RelativeLayout group, RelativeLayout skipView, AdsContext.AdsFrom from){
        splashMap.get(from).launchAds(context,group,skipView,mHandle);
    }
    public void exitSplashAds(Context context,RelativeLayout group, AdsContext.AdsFrom from){
        splashMap.get(from).lanchExit(context,group);
    }

}
