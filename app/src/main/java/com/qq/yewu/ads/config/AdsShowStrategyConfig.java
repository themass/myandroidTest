package com.qq.yewu.ads.config;

import com.qq.common.util.LogUtil;

import java.util.Arrays;
import java.util.List;


/**
 * Created by dengt on 2017/9/20.
 */

public class AdsShowStrategyConfig {
    private int bannerIndex = 0;
    public List<String> banners = Arrays.asList("adview");
    public List<String> splash;
    public String getNextBanner(){
        if(bannerIndex<banners.size()){
            bannerIndex++;
            LogUtil.i("获取banner广告："+banners.get(bannerIndex));
            return banners.get(bannerIndex);
        }else{
            initBanner();
            return null;
        }
    }
    public void initBanner(){
        LogUtil.i("init banner");
        bannerIndex=0;
    }

}
