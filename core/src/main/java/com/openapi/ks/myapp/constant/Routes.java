package com.openapi.ks.myapp.constant;

import android.content.Context;
import android.content.Intent;

import java.io.Serializable;

/**
 * 跨功能的页面入口。功能模块之间不直接引用对方的 Activity 类，而是通过这里用类名打开
 * （对话打开拍照解题也是这样做的）。
 */
public final class Routes {

    /** 登录页，在 :feature-user。 */
    public static final String LOGIN = "com.openapi.ks.myapp.ui.user.LoginActivity";

    /** 视频播放页，在 :feature-video。参数用 Constants.CONFIG_PARAM 传递。 */
    public static final String VIDEO_SHOW = "com.openapi.ks.myapp.ui.sound.VideoShowActivity";

    /** 延迟取地址的视频播放页，在 :feature-video。 */
    public static final String VIDEO_SHOW_LAZY_URL = "com.openapi.ks.myapp.ui.sound.VideoShowActivityLazyUrl";

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

    private static Intent withParam(Intent intent, Serializable param) {
        intent.putExtra(Constants.CONFIG_PARAM, param);
        return intent;
    }
}
