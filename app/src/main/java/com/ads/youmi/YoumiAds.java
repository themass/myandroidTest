package com.ads.youmi;

import android.content.Context;
import android.widget.Toast;

import com.sspacee.common.util.DeviceInfoUtils;
import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.R;

import net.youmi.android.AdManager;
import net.youmi.android.listener.Interface_ActivityListener;
import net.youmi.android.os.EarnPointsOrderList;
import net.youmi.android.os.OffersBrowserConfig;
import net.youmi.android.os.OffersManager;
import net.youmi.android.os.PointsEarnNotify;
import net.youmi.android.os.PointsManager;

/**
 * Created by themass on 2017/9/14.
 */

public class YoumiAds{
    private static final String publiserId = "8521b58e067666ba";
    private static final String appSecret = "a6daf867d009598d";
    private static boolean preloadVideoOk = false;
    public static PointsEarnNotify pointsListener = new PointsEarnNotify() {
        public void onPointEarn(Context var1, EarnPointsOrderList var2) {
            Toast.makeText(var1, R.string.offerads_ths, Toast.LENGTH_SHORT).show();
        }
    };
    public  static void init(Context context) {
        AdManager.getInstance(context).init(publiserId, appSecret, true);
        OffersManager.getInstance(context).onAppLaunch();
        PointsManager.getInstance(context).registerPointsEarnNotify(pointsListener);
         PointsManager.getInstance(context).setEnableEarnPointsToastTips(false);
        // userid 不能为空 或者 空串,否则设置无效, 字符串长度必须要小于50
    }
    public static void offerAds(Context context){
        OffersManager.getInstance(context).setCustomUserId(DeviceInfoUtils.getDeviceId(context));
        // 有米Android SDK v4.10之后的sdk还需要配置下面代码，以告诉sdk使用了服务器回调
        OffersManager.getInstance(context).setUsingServerCallBack(true);
        OffersBrowserConfig.getInstance(context).setBrowserTitleText(context.getResources().getString(R.string.menu_btn_support));
        // 设置积分墙标题背景颜色
        OffersBrowserConfig.getInstance(context).setBrowserTitleBackgroundColor(context.getResources().getColor(R.color.style_color_primary));
        // 设置积分余额区域是否显示
        // true ：显示（默认值）
        // false：不显示
        OffersBrowserConfig.getInstance(context).setPointsLayoutVisibility(false);
        checkConfig(context);

        OffersManager.getInstance(context).showOffersWall(new Interface_ActivityListener() {
            /**
             * 当积分墙销毁的时候，即积分墙的Activity调用了onDestory的时候回调
             */
            @Override
            public void onActivityDestroy(Context context) {
                LogUtil.i("退出积分墙");
            }
        });
    }

    private static void checkConfig(Context context) {
        StringBuilder sb = new StringBuilder();

        addTextToSb(sb,
                OffersManager.getInstance(context).checkOffersAdConfig() ? "广告配置结果：正常" :
                        "广告配置结果：异常，具体异常请查看Log，Log标签：YoumiSdk"
        );
        addTextToSb(sb, "%s服务器回调", OffersManager.getInstance(context).isUsingServerCallBack() ? "已经开启" : "没有开启");
        addTextToSb(sb,
                "%s通知栏下载相关的通知",
                AdManager.getInstance(context).isDownloadTipsDisplayOnNotification() ? "已经开启" : "没有开启"
        );
        addTextToSb(sb,
                "%s通知栏安装成功的通知",
                AdManager.getInstance(context).isInstallationSuccessTipsDisplayOnNotification() ? "已经开启" : "没有开启"
        );
        addTextToSb(sb,
                "%s通知栏赚取积分的提示",
                PointsManager.getInstance(context).isEnableEarnPointsNotification() ? "已经开启" : "没有开启"
        );
        addTextToSb(sb,
                "%s积分赚取的Toast提示",
                PointsManager.getInstance(context).isEnableEarnPointsToastTips() ? "已经开启" : "没有开启"
        );
        LogUtil.i(sb.toString());
    }

    /**
     * 格式化字符串
     */
    private static void addTextToSb(StringBuilder sb, String format, Object... args) {
        sb.append(String.format(format, args));
        sb.append(System.getProperty("line.separator"));
    }

}
