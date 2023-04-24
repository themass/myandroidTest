package com.openapi.yewu.ads.admob;

import android.content.Context;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.openapi.common.util.LogUtil;


/**
 * Created by dengt on 2017/9/20.
 */

public class AdmobAdsManager {

    public static void init(Context context) {
        MobileAds.initialize(context, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
                LogUtil.i("AdmobAdsManager init- "+initializationStatus.getAdapterStatusMap());
            }
        });

    }
}
