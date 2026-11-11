package com.openapi.core.config;

import android.content.Context;
import android.content.Intent;

import com.openapi.common.ads.base.AdsContext;
import com.openapi.common.util.android.StaticDataUtil;
import com.openapi.core.model.RecommendVo;
import com.openapi.core.legacyui.base.CommonFragmentActivity;

import java.io.Serializable;

/**
 * 跨功能的页面入口。功能模块之间不直接引用对方的 Activity 类，而是通过这里用类名打开
 * （对话打开拍照解题也是这样做的）。
 */
public final class Routes {

    /** 登录页，在 :feature-user。 */
    public static final String LOGIN = "com.openapi.feature.user.login.LoginActivity";

    /** 视频播放页，在 :feature-video。参数用 Constants.CONFIG_PARAM 传递。 */
    public static final String VIDEO_SHOW = "com.openapi.feature.video.page.VideoShowActivity";

    /** 延迟取地址的视频播放页，在 :feature-video。 */
    public static final String VIDEO_SHOW_LAZY_URL = "com.openapi.feature.video.page.VideoShowActivityLazyUrl";

    /** 设置页，在 :feature-user。 */
    public static final String SETTING = "com.openapi.feature.user.setting.SettingActivity";

    /** 全局搜索结果页（频道里的视频列表），在 :feature-content。 */
    public static final String GLOBAL_VIDEO_SEARCH = "com.openapi.feature.content.video.GlobalVideoChannelListFragment";

    private Routes() {
    }

    public static Intent login(Context context) {
        return new Intent().setClassName(context.getPackageName(), LOGIN);
    }

    public static Intent videoShow(Context context, Serializable param) {
        return withParam(new Intent().setClassName(context.getPackageName(), VIDEO_SHOW), param);
    }

    public static Intent videoShowLazyUrl(Context context, Serializable param) {
        return withParam(new Intent().setClassName(context.getPackageName(), VIDEO_SHOW_LAZY_URL), param);
    }

    public static Intent setting(Context context) {
        return new Intent().setClassName(context.getPackageName(), SETTING);
    }

    /** 全局搜索：用 vo.param 作为关键词打开视频列表页。 */
    public static Intent globalSearch(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT_NAME, GLOBAL_VIDEO_SEARCH);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.title);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN1);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
        StaticDataUtil.add(Constants.VIDEO_CHANNEL, vo);
        return intent;
    }

    private static Intent withParam(Intent intent, Serializable param) {
        intent.putExtra(Constants.CONFIG_PARAM, param);
        return intent;
    }
}
