package com.openapi.yewu.ads.mobvista;

import android.content.Context;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.openapi.myapp.constant.Constants;

import java.util.Map;


/**
 * Created by dengt on 2017/9/20.
 */

public class MobvistaAdsManager {

    public static void init(Context context) {
        // test appId and appKey
//        MBridgeSDK sdk = MBridgeSDKFactory.getMBridgeSDK();
//        Map<String, String> map = sdk.getMBConfigurationMap(Constants.Mob_APPID, Constants.Mob_APPKEY);
//        sdk.init(map, context, new SDKInitStatusListener() {
//            @Override
//            public void onInitSuccess() {
//                LogUtil.i("MobvistaAdsManager onInitSuccess");
//            }
//
//            @Override
//            public void onInitFail(String errorMsg) {
//                LogUtil.e("MobvistaAdsManager SDKInitStatusFail "+errorMsg);
//            }
//        });
    }
}
