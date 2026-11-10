package com.openapi.ks.myapp.constant;

import android.content.Context;
import android.content.Intent;

/**
 * 跨功能的页面入口。功能模块之间不直接引用对方的 Activity 类，而是通过这里用类名打开
 * （对话打开拍照解题也是这样做的）。
 */
public final class Routes {

    /** 登录页，在 :feature-user。 */
    public static final String LOGIN = "com.openapi.ks.myapp.ui.user.LoginActivity";

    private Routes() {
    }

    public static Intent login(Context context) {
        return new Intent().setClassName(context.getPackageName(), LOGIN);
    }
}
