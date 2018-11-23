package com.qq.ads.base;

import com.kyview.natives.NativeAdInfo;

import java.util.List;

public interface NativeAdsReadyListener {
        boolean onAdRecieved(List<NativeAdInfo> data);
    }