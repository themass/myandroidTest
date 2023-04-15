package com.openapi.yewu.ads.base;


import com.openapi.myapp.bean.vo.NativeAdInfo;

import java.util.List;

public interface NativeAdsReadyListener {
        boolean onAdRecieved(List<NativeAdInfo> data);
    }