package com.openapi.core.app;

import android.content.Context;

/** 进程级应用上下文：由 Application.onCreate 设置一次，供 :core 里的基类使用，避免依赖 :app 的 Application 类。 */
public final class AppContext {

    /** 登录状态变化广播（原 MyApplication.UPDATE_STATUS_ACTION）。 */
    public static final String UPDATE_STATUS_ACTION = "com.openapi.ks.chat.action.UPDATE_STATUS";

    private static volatile Context app;

    private AppContext() {
    }

    public static void init(Context applicationContext) {
        app = applicationContext.getApplicationContext();
    }

    public static Context get() {
        Context c = app;
        if (c == null) {
            throw new IllegalStateException("AppContext.init() not called");
        }
        return c;
    }
}
