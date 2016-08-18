package com.timeline.vpn.ads.interstitial;

import com.timeline.vpn.common.util.CollectionUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by gqli on 2016/3/23.
 */
public class InterstitialAdsStrategy {
    private static List<String> DEAFULT = new ArrayList<>();
    private int index=0;
    private List<String> mode = DEAFULT;
    static {
        DEAFULT.addAll(Arrays.asList(Constants.LAUNCH_DUOM,Constants.LAUNCH_BAIDU,Constants.LAUNCH_QQ,Constants.LAUNCH_YOUMI));
    }
    public InterstitialAdsStrategy(List<String> list){
        if(!CollectionUtils.isEmpty(list)){
            LogUtil.i("ops------------------");
            this.mode=list;
        }
    }
    public String getNext(){
        if(index>=mode.size()){
            return null;
        }
        return mode.get(index++);
    }
    public void init(){
        index=0;
    }

}
