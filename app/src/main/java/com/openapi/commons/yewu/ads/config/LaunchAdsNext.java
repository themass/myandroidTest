package com.openapi.commons.yewu.ads.config;

import com.openapi.commons.yewu.ads.base.AdsContext;

/**
 * Created by openapi on 2017/9/15.
 */

public class LaunchAdsNext {
    public LaunchAdsNext(AdsContext.AdsFrom from){
        this.from = from;
    }
    public AdsContext.AdsFrom from;
}
