package com.timeline.vpn.ads.interstitial;

import android.app.Activity;
import android.os.Handler;

import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by gqli on 2016/3/23.
 */
public class InterstitialProxy {
    public static InterstitialAdsStrategy DEAFULT_STRATEGY = new InterstitialAdsStrategy(null);
    private InterstitialAdsStrategy strategy;
    private Activity context;
    private Handler handler;
    Map<String,InterstitialAdsController> map = new HashMap<>();
    public InterstitialProxy(InterstitialAdsStrategy strategy, Activity context, Handler handler){
        this.context = context;
        this.handler = handler;
        this.strategy = strategy;
    }
    public InterstitialAdsController getController(){
        String next = strategy.getNext();
        if(next!=null){
            LogUtil.i(next);
            InterstitialAdsController controller = map.get(next);
            if(controller!=null){
                return controller;
            }
            switch (next){
                case Constants.LAUNCH_DUOM:
                    controller = new DuomInterstitialAdsController(context,handler);
                    break;
                case Constants.LAUNCH_BAIDU:
                    controller = new BaiduInterstitialAdsController(context,handler);
                    break;
                case Constants.LAUNCH_QQ:
                    controller = new QQInterstitialAdsController(context,handler);
                    break;
                case Constants.LAUNCH_YOUMI:
                    controller = new YoumiInterstitialAdsController(context,handler);
                    break;
                default:
                    return null;
            }
            map.put(next,controller);
            return controller;
        }
        strategy.init();
        return null;
    }

}
