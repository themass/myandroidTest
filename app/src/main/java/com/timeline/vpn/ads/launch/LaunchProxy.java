package com.timeline.vpn.ads.launch;

import android.app.Activity;
import android.os.Handler;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by gqli on 2016/3/23.
 */
public class LaunchProxy{
    public static LaunchAdsStrategy DEAFULT_STRATEGY = new LaunchAdsStrategy(null);
    private LaunchAdsStrategy strategy;
    private Activity context;
    private ViewGroup adsGroup;
    private Handler handler;
    private ImageButton ibSkip;
    Map<String,LaunchAdsController> map = new HashMap<>();
    public LaunchProxy(LaunchAdsStrategy strategy,Activity context,ViewGroup adsGroup,Handler handler,ImageButton ibSkip){
        this.adsGroup = adsGroup;
        this.context = context;
        this.handler = handler;
        this.ibSkip = ibSkip;
        this.strategy = strategy;
    }
    public LaunchAdsController getController(){
        String next = strategy.getNext();
        if(next!=null){
            LogUtil.i(next);
            LaunchAdsController controller = map.get(next);
            if(controller!=null){
                return controller;
            }
            switch (next){
                case Constants.LAUNCH_DUOM:
                    controller = new DuomLaunchController(context,adsGroup,handler,ibSkip);
                    break;
                case Constants.LAUNCH_QQ:
                    controller = new QQLaunchController(context,adsGroup,handler,ibSkip);
                    break;
                case Constants.LAUNCH_BAIDU:
                    controller = new BaiduLaunchController(context,adsGroup,handler,ibSkip);
                    break;
                case Constants.LAUNCH_YOUMI:
                    controller = new YoumiLaunchController(context,adsGroup,handler,ibSkip);
                    break;
                default:
                    return null;
            }
            map.put(next,controller);
            return controller;
        }
        return null;
    }
    public void init(){
        strategy.init();
    }
}
