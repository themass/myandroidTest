package com.openapi.core.app;

import android.app.Activity;
import android.content.Context;

import com.openapi.ks.myapp.bean.vo.RecommendVo;

/**
 * core 与 feature 之间的端口：core 里的基类只通过这里调用用户、登录、设置、搜索能力，
 * 具体实现由 :app 在启动时注册（见 AppHooksImpl）。这样 :core 不依赖任何 :feature-* 模块。
 */
public final class AppHooks {

    public interface Provider {
        boolean isVip();

        void openGlobalSearch(Activity activity, RecommendVo vo);

        void openSetting(Context context);

        void openLogin(Context context);
    }

    private static volatile Provider provider;

    private AppHooks() {
    }

    public static void install(Provider p) {
        provider = p;
    }

    public static Provider get() {
        Provider p = provider;
        if (p == null) {
            throw new IllegalStateException("AppHooks.install() not called");
        }
        return p;
    }
}
