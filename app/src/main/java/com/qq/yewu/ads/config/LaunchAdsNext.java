package com.qq.yewu.ads.config;

import com.qq.yewu.ads.base.AdsContext;

/**
 * Created by dengt on 2017/9/15.
 */

public class LaunchAdsNext {
    public LaunchAdsNext(AdsContext.AdsFrom from){
        this.from = from;
    }
    public AdsContext.AdsFrom from;
}