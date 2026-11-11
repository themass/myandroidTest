package com.openapi.common.ads.base;


import java.util.List;

public interface NativeAdsReadyListener {
    boolean onAdRecieved(List<NativeAdInfo> data);

    public static class NativeAdInfo {
    }

}