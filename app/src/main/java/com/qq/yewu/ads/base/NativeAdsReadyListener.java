package com.qq.yewu.ads.base;


import com.qq.myapp.bean.vo.NativeAdInfo;

import java.util.List;

public interface NativeAdsReadyListener {
        boolean onAdRecieved(List<NativeAdInfo> data);
    }