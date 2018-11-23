package com.qq.ads.config;


import com.qq.ext.util.LogUtil;

import java.util.Arrays;
import java.util.List;


/**
 * Created by themass on 2017/9/20.
 */

public class AdsShowStrategyConfig {
    private int bannerIndex = 0;
    public List<String> banners = Arrays.asList("adview","gdt");
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
