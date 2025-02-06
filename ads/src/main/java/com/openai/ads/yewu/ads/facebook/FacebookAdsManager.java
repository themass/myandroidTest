package com.openai.ads.yewu.ads.facebook;

import static com.openai.CommonConstants.DEBUG;

import android.content.Context;

import com.facebook.ads.AdSettings;
import com.facebook.ads.AudienceNetworkAds;
import com.openai.commonutil.LogUtil;


/**
 * Created by dengt on 2017/9/20.
 */

public class FacebookAdsManager implements AudienceNetworkAds.InitListener {

    public static void init(Context context) {
        if (!AudienceNetworkAds.isInitialized(context)) {
            if (DEBUG) {
                AdSettings.turnOnSDKDebugger(context);
            }

            AudienceNetworkAds
                    .buildInitSettings(context)
                    .withInitListener(new FacebookAdsManager())
                    .initialize();
        }
    }

    @Override
    public void onInitialized(AudienceNetworkAds.InitResult result) {
        LogUtil.i(AudienceNetworkAds.TAG + "---" + result.getMessage());
    }
}
