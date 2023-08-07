package com.openapi.commons.yewu.ads.base;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import androidx.fragment.app.FragmentActivity;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.yewu.ads.adview.AdviewAdsManager;
import com.openapi.commons.yewu.ads.adview.BannerAdviewAds;
import com.openapi.commons.yewu.ads.adview.InterstitialAdviewAds;
import com.openapi.commons.yewu.ads.adview.SplashAdviewAds;
import com.openapi.commons.yewu.ads.adview.VideoAdviewAds;
import com.openapi.ks.myapp.base.MyApplication;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by openapi on 2017/9/20.
 */

public class AdsManager {
    static AdsManager manager = new AdsManager();
    private AdsManager(){}
    private Map<AdsContext.AdsFrom,SplashAdsInter> splashMap = new HashMap<>();
    private Map<AdsContext.AdsFrom,BannerInter> bannerDescMap = new HashMap<>();
    private Map<AdsContext.AdsFrom,InterstitialAdsInter> interstitialMap = new HashMap<>();
    private Map<AdsContext.AdsFrom,VideoAdsInter> videoMap = new HashMap<>();
    public static AdsManager getInstans(){
        return  manager;
    }
    public void init(Context context){
        LogUtil.i("AdsManager initok");
        AdviewAdsManager.init(context);
        bannerDescMap.put(AdsContext.AdsFrom.ADVIEW,new BannerAdviewAds());
        interstitialMap.put(AdsContext.AdsFrom.ADVIEW,new InterstitialAdviewAds());
        splashMap.put(AdsContext.AdsFrom.ADVIEW,new SplashAdviewAds());
        videoMap.put(AdsContext.AdsFrom.ADVIEW, new VideoAdviewAds());
    }
    private Handler mHandle = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            AdsContext.AdsMsgObj obj = (AdsContext.AdsMsgObj)msg.obj;
            LogUtil.i("Adstype = "+obj.type.name()+"; status="+obj.status.name()+"; from="+obj.from.name());
            AdsContext.adsNotify(MyApplication.getInstance(),obj.type,obj.status);
//            if(obj.type== AdsContext.AdsType.ADS_TYPE_BANNER && obj.status== AdsContext.AdsShowStatus.ADS_NO_MSG){
//                EventBusUtil.getEventBus().post(new BannerAdsNext(obj.from));
//            }
//            if(obj.type== AdsContext.AdsType.ADS_TYPE_SPREAD && obj.status== AdsContext.AdsShowStatus.ADS_NO_MSG){
//                EventBusUtil.getEventBus().post(new LaunchAdsNext(obj.from));
//            }
            super.handleMessage(msg);
        }
    };
    public void showBannerAds(FragmentActivity context, ViewGroup group,AdsContext.Categrey categrey){
            bannerDescMap.get(AdsContext.AdsFrom.ADVIEW).bannerAds(context,group,categrey.key,mHandle);
    }
    public void exitBannerAds(FragmentActivity context, ViewGroup group,AdsContext.Categrey categrey){
        bannerDescMap.get(AdsContext.AdsFrom.ADVIEW).bannerExit(context,group,categrey.key);
    }
    public void showSplashAds(FragmentActivity context, RelativeLayout group, RelativeLayout skipView){
        splashMap.get(AdsContext.AdsFrom.ADVIEW).launchAds(context,group,skipView,mHandle);
    }
    public void exitSplashAds(Context context,RelativeLayout group){
        splashMap.get(AdsContext.AdsFrom.ADVIEW).lanchExit(context,group);
    }

    public void showInterstitialAds(Context context, AdsContext.Categrey categrey, boolean score){
        interstitialMap.get(AdsContext.AdsFrom.ADVIEW).interstitialAds(context,mHandle,categrey.key,score);
    }
    public void exitInterstitialAds(Context context,AdsContext.Categrey categrey){
        interstitialMap.get(AdsContext.AdsFrom.ADVIEW).interstitialExit(context,categrey.key);
    }
    public void offerAds(Context context){

//        YoumiAds.offerAds(context);
    }
    public  void reqVideo(Context context){
        videoMap.get(AdsContext.AdsFrom.ADVIEW).reqVideo(context,mHandle);
    }
    public  boolean showVideo(Context context){
        VideoAdviewAds ads = (VideoAdviewAds)videoMap.get(AdsContext.AdsFrom.ADVIEW);
        if(ads.isReq) {
            videoMap.get(AdsContext.AdsFrom.ADVIEW).showVideo(context);
            return true;
        }else {
            reqVideo(context);
            return false;
        }
    }
    public  void closeVideo(Context context){
        videoMap.get(AdsContext.AdsFrom.ADVIEW).exitVideo(context);
    }
}
