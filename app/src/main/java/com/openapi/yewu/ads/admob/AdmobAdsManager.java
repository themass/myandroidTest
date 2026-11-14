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

    /**
     * 初始化 AdMob。在 SDK 初始化完成后再走 UMP 同意流程——
     * UMP 需要 SDK 已就绪才能展示表单，顺序反了会拿到空配置。
     */
    public static void init(final Context context) {
        MobileAds.initialize(context, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
                LogUtil.i("AdmobAdsManager init- "+initializationStatus.getAdapterStatusMap());
                com.openapi.yewu.ads.admob.AdConsent.requestIfNeeded(context);
            }
        });
    }
}
