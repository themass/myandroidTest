package com.timeline.vpn.ads.banner;

import com.timeline.vpn.common.util.CollectionUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by gqli on 2016/3/23.
 */
public class BannerAdsStrategy {
    private static List<String> DEAFULT = new ArrayList<>();
    private int index=0;
    private List<String> mode = DEAFULT;
    static {
        DEAFULT.addAll(Arrays.asList(Constants.LAUNCH_DUOM,Constants.LAUNCH_BAIDU));
    }
    public BannerAdsStrategy(List<String> list){
        if(!CollectionUtils.isEmpty(list)){
            LogUtil.i("ops------------------");
            this.mode=list;
        }
    }
    public String getNext(int postion){
        return mode.get(postion%mode.size());
    }
    public void init(){
        index=0;
    }
    public int size(){
        return mode.size();
    }

}
