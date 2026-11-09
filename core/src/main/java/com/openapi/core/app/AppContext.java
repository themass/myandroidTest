package com.openapi.core.app;

import android.content.Context;

/** 进程级应用上下文：由 Application.onCreate 设置一次，供 :core 里的基类使用，避免依赖 :app 的 Application 类。 */
public final class AppContext {

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
