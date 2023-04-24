package com.openapi.yewu.ads.base;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import androidx.fragment.app.FragmentActivity;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.openapi.common.util.EventBusUtil;
import com.openapi.common.util.LogUtil;
import com.openapi.yewu.ads.admob.AdmobAdsManager;
import com.openapi.yewu.ads.admob.AdmobBannerAds;
import com.openapi.yewu.ads.admob.AdmobInterstitialAds;
import com.openapi.yewu.ads.admob.AdmobSplashAds;
import com.openapi.yewu.ads.adview.AdviewAdsManager;
import com.openapi.yewu.ads.adview.BannerAdviewAds;
import com.openapi.yewu.ads.adview.InterstitialAdviewAds;
import com.openapi.yewu.ads.adview.NativeAdviewAds;
import com.openapi.yewu.ads.adview.SplashAdviewAds;
import com.openapi.yewu.ads.adview.VideoAdviewAds;
import com.openapi.yewu.ads.config.BannerAdsNext;
import com.openapi.yewu.ads.config.LaunchAdsNext;
import com.openapi.yewu.ads.mobvista.MobivistaBannerAds;
import com.openapi.yewu.ads.mobvista.MobvistaInterstitialAds;
import com.openapi.yewu.ads.mobvista.MobvistaAdsManager;
import com.openapi.yewu.ads.mobvista.MobvistaSplashAds;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by dengt on 2017/9/20.
 */

public class AdsManager {
    static AdsManager manager = new AdsManager();
    private AdsManager(){}
    private Map<AdsContext.AdsFrom,SplashAdsInter> splashMap = new HashMap<>();
    private Map<AdsContext.AdsFrom,BannerInter> bannerDescMap = new HashMap<>();
    private Map<AdsContext.AdsFrom,InterstitialAdsInter> interstitialMap = new HashMap<>();
    private Map<AdsContext.AdsFrom,NativeAdsInter> nativeMap = new HashMap<>();
    private Map<AdsContext.AdsFrom,VideoAdsInter> videoMap = new HashMap<>();
    public static AdsManager getInstans(){
        return  manager;
    }
    public void init(Context context){
        LogUtil.i("AdsManager initok");
        AdviewAdsManager.init(context);
        AdmobAdsManager.init(context);
        MobvistaAdsManager.init(context);
        bannerDescMap.put(AdsContext.AdsFrom.ADVIEW,new BannerAdviewAds());
        bannerDescMap.put(AdsContext.AdsFrom.MOBVISTA,new MobivistaBannerAds());
        bannerDescMap.put(AdsContext.AdsFrom.ADMOB,new AdmobBannerAds());
        interstitialMap.put(AdsContext.AdsFrom.ADVIEW,new InterstitialAdviewAds());
        interstitialMap.put(AdsContext.AdsFrom.MOBVISTA,new MobvistaInterstitialAds());
        interstitialMap.put(AdsContext.AdsFrom.ADMOB,new AdmobInterstitialAds());
        splashMap.put(AdsContext.AdsFrom.ADVIEW,new SplashAdviewAds());
        splashMap.put(AdsContext.AdsFrom.MOBVISTA,new MobvistaSplashAds());
        splashMap.put(AdsContext.AdsFrom.ADMOB,new AdmobSplashAds());
        nativeMap.put(AdsContext.AdsFrom.ADVIEW,new NativeAdviewAds());
        videoMap.put(AdsContext.AdsFrom.ADVIEW, new VideoAdviewAds());
        JSONObject consentObject = new JSONObject();
//        final MIntegralSDK sdk = MIntegralSDKFactory.getMIntegralSDK();
//        Map<String, String> map = sdk.getMTGConfigurationMap(Constants.Mob_APPID, Constants.Mob_APPKEY);
//        // if you modify applicationId, please add the following attributes,
//        // otherwise it will crash
//        // map.put(MIntegralConstans.PACKAGE_NAME_MANIFEST, "your AndroidManifest
//        // package value");
//
//        sdk.init(map, context);
    }
    private Handler mHandle = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            AdsContext.AdsMsgObj obj = (AdsContext.AdsMsgObj)msg.obj;
            LogUtil.i("Adstype = "+obj.type.name()+"; status="+obj.status.name()+"; from="+obj.from.name());
//            AdsContext.adsNotify(MyApplication.getInstance(),obj.type,obj.status);
            if(obj.type== AdsContext.AdsType.ADS_TYPE_BANNER && obj.status== AdsContext.AdsShowStatus.ADS_NO_MSG){
                EventBusUtil.getEventBus().post(new BannerAdsNext(obj.from));
            }
            if(obj.type== AdsContext.AdsType.ADS_TYPE_SPREAD && obj.status== AdsContext.AdsShowStatus.ADS_NO_MSG){
                EventBusUtil.getEventBus().post(new LaunchAdsNext(obj.from));
            }
            if(obj.type== AdsContext.AdsType.ADS_TYPE_INTERSTITIAL && obj.status== AdsContext.AdsShowStatus.ADS_NO_MSG && obj.from== AdsContext.AdsFrom.ADVIEW && obj.addCount()){
                showInterstitialAds(obj.context,AdsContext.Categrey.CATEGREY_VPN,false,AdsContext.AdsFrom.MOBVISTA,obj.count);
            }else if(obj.type== AdsContext.AdsType.ADS_TYPE_INTERSTITIAL && obj.status== AdsContext.AdsShowStatus.ADS_NO_MSG && obj.from== AdsContext.AdsFrom.MOBVISTA && obj.addCount()){
                showInterstitialAds(obj.context,AdsContext.Categrey.CATEGREY_VPN,false,AdsContext.AdsFrom.ADVIEW,obj.count);
            }else if(obj.type== AdsContext.AdsType.ADS_TYPE_INTERSTITIAL && obj.status== AdsContext.AdsShowStatus.ADS_NO_MSG && obj.from== AdsContext.AdsFrom.ADMOB && obj.addCount()){
                showInterstitialAds(obj.context,AdsContext.Categrey.CATEGREY_VPN,false,AdsContext.AdsFrom.MOBVISTA,obj.count);
            }
            super.handleMessage(msg);
        }
    };
    public void offerAds(Context context){
    }
    public void showBannerAds(FragmentActivity context, ViewGroup group, AdsContext.Categrey categrey){
            bannerDescMap.get(AdsContext.AdsFrom.ADVIEW).bannerAds(context,group,categrey.key,mHandle);
    }
    public void showBannerAds(FragmentActivity context, ViewGroup group, AdsContext.Categrey categrey, AdsContext.AdsFrom from){
        bannerDescMap.get(from).bannerAds(context,group,categrey.key,mHandle);
    }
    public void exitBannerAds(FragmentActivity context, ViewGroup group, AdsContext.Categrey categrey){
        bannerDescMap.get(AdsContext.AdsFrom.ADVIEW).bannerExit(context,group,categrey.key);
    }
    public void showSplashAds(AdsContext.AdsFrom from ,FragmentActivity context, RelativeLayout group, RelativeLayout skipView){
        splashMap.get(from).launchAds(context,group,skipView,mHandle);
    }

    public void exitSplashAds(Context context,RelativeLayout group){
        splashMap.get(AdsContext.AdsFrom.ADMOB).lanchExit(context,group);
    }

    public void showInterstitialAds(Context context, AdsContext.Categrey categrey, boolean score){
        interstitialMap.get(AdsContext.AdsFrom.ADVIEW).interstitialAds(context,mHandle,categrey.key,score,0);
    }
    public void showInterstitialAds(Context context, AdsContext.Categrey categrey, boolean score,AdsContext.AdsFrom from){
        interstitialMap.get(from).interstitialAds(context,mHandle,categrey.key,score,0);
    }
    public void showInterstitialAds(Context context, AdsContext.Categrey categrey, boolean score,AdsContext.AdsFrom from,int count){
        interstitialMap.get(from).interstitialAds(context,mHandle,categrey.key,score,count);
    }
    public void exitInterstitialAds(Context context,AdsContext.Categrey categrey){
        interstitialMap.get(AdsContext.AdsFrom.ADVIEW).interstitialExit(context,categrey.key);
    }
    public  void showNative(Context context, NativeAdsReadyListener listener,AdsContext.Categrey categrey){
        nativeMap.get(AdsContext.AdsFrom.ADVIEW).showNative(context,mHandle,listener,categrey.key);
    }
    public  void reqVideo(Context context){
        videoMap.get(AdsContext.AdsFrom.ADVIEW).reqVideo(context,mHandle);
    }
    public  boolean showVideo(Context context){
        LogUtil.i("Adview req video ads");
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
