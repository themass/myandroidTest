package com.ads.config;


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
            return banners.get(bannerIndex);
        }else{
            initBanner();
            return null;
        }
    }
    public void initBanner(){
        bannerIndex=0;
    }

}
