package com.qq.ads.adview;

import android.content.Context;

import com.kyview.InitConfiguration;
import com.kyview.manager.AdViewBannerManager;
import com.kyview.manager.AdViewInstlManager;
import com.kyview.manager.AdViewNativeManager;
import com.kyview.manager.AdViewSpreadManager;
import com.kyview.manager.AdViewVideoManager;

import static com.qq.ads.adview.AdviewConstant.adsKeySet;


/**
 * Created by dengt on 2017/9/20.
 */

public class AdviewAdsManager {
    public  static InitConfiguration initConfig;
    public static void init(Context context) {
        InitConfiguration.Builder builder = new InitConfiguration.Builder(context)
                .setUpdateMode(InitConfiguration.UpdateMode.EVERYTIME)   // 实时获取配置
                .setBannerCloseble(InitConfiguration.BannerSwitcher.DEFAULT)    //横幅可关闭按钮
                .setInstlDisplayType(InitConfiguration.InstlDisplayType.POPUPWINDOW_MODE)// 为默认情况,设置插屏展示模式，popupwindow模式可设置窗体外可点击
                .setInstlCloseble(InitConfiguration.InstlSwitcher.CANCLOSED)     //插屏可关闭按钮
                .setAdInMobiSize(InitConfiguration.AdInMobiSize.INMOBI_AD_UNIT_320x50);
        builder.setAdMobSize(InitConfiguration.AdMobSize.BANNER);
        builder.setAdGdtSize(InitConfiguration.AdGdtSize.BANNER);
        builder.setAdInMobiSize(InitConfiguration.AdInMobiSize.INMOBI_AD_UNIT_468x60);
        builder.setAdSize(InitConfiguration.AdSize.BANNER_SMART);
//        if (MyApplication.isDebug) {
//            builder.setRunMode(InitConfiguration.RunMode.TEST);
//        } else {
//            builder.setRunMode(InitConfiguration.RunMode.NORMAL);
//        }
        builder.setRunMode(InitConfiguration.RunMode.NORMAL);
        initConfig = builder.build();
        AdViewSpreadManager.getInstance(context).init(initConfig, adsKeySet);
        AdViewBannerManager.getInstance(context).init(initConfig, adsKeySet);
        AdViewInstlManager.getInstance(context).init(initConfig, adsKeySet);
        AdViewNativeManager.getInstance(context).init(initConfig, adsKeySet);
        AdViewVideoManager.getInstance(context).init(initConfig, adsKeySet);
    }
}
