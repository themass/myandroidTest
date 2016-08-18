package com.timeline.vpn.ads.banner;

import android.app.Activity;
import android.os.Handler;
import android.view.ViewGroup;

import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by gqli on 2016/3/23.
 */
public class BannerProxy{
    public static BannerAdsStrategy DEAFULT_STRATEGY = new BannerAdsStrategy(null);
    private BannerAdsStrategy strategy;
    private Activity context;
    private Handler handler;
    Map<String,BannerAdsController> map = new HashMap<>();
    public BannerProxy(BannerAdsStrategy strategy,Activity context,Handler handler){
        this.context = context;
        this.handler = handler;
        this.strategy = strategy;
    }
    public BannerAdsController getController(int postion,ViewGroup adsView){
        String next = strategy.getNext(postion);
        if(next!=null){
            LogUtil.i(next);
            BannerAdsController controller = map.get(next);
            if(controller!=null){
                return controller;
            }
            switch (next){
                case Constants.LAUNCH_DUOM:
                    controller = new DuomBannerController(context,handler,adsView);
                    break;
                case Constants.LAUNCH_QQ:
                    break;
                case Constants.LAUNCH_BAIDU:
                    controller = new BaidduBannerController(context,handler,adsView);
                    break;
                case Constants.LAUNCH_YOUMI:
                    break;
                default:
                    return null;
            }
            map.put(next,controller);
            return controller;
        }
        return null;
    }
    public int size(){
        return strategy.size();
    }
    public void init(){
        strategy.init();
    }
}
