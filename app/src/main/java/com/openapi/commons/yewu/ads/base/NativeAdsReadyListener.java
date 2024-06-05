package com.openapi.commons.yewu.ads.base;


import com.openapi.ks.myapp.bean.vo.NativeAdInfo;

import java.util.List;

public interface NativeAdsReadyListener {
    boolean onAdRecieved(List<NativeAdInfo> data);
}