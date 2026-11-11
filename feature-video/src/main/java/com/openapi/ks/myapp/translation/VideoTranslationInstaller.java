package com.openapi.ks.myapp.translation;

import android.content.Context;

import com.openapi.core.view.translation.VideoTranslationRegistry;
import com.openapi.common.util.android.PreferenceUtils;
import com.openapi.ks.myapp.constant.Constants;

/**
 * 注册所有 {@link com.openapi.core.view.translation.VideoTranslationProvider}，并读取用户选择的当前实现。
 */
public final class VideoTranslationInstaller {

    private VideoTranslationInstaller() {
    }

    /**
     * 在 {@link com.openapi.ks.myapp.base.MyApplication#onCreate} 中调用一次。
     */
    public static void install(Context context) {
        VideoTranslationRegistry r = VideoTranslationRegistry.getInstance();
        r.register(new DemoVideoTranslationProvider());
        r.register(new StubBaiduVideoTranslationProvider());
        r.register(new StubVolcanoVideoTranslationProvider());

        String id = PreferenceUtils.getPrefString(context, Constants.TRANSLATION_PROVIDER_ID,
                DemoVideoTranslationProvider.ID);
        r.setActiveProviderId(id);
    }

    /** 切换当前翻译引擎并持久化，例如 {@link StubBaiduVideoTranslationProvider#ID} / {@link StubVolcanoVideoTranslationProvider#ID}。 */
    public static void setActiveProvider(Context context, String providerId) {
        PreferenceUtils.setPrefString(context, Constants.TRANSLATION_PROVIDER_ID, providerId);
        VideoTranslationRegistry.getInstance().setActiveProviderId(providerId);
    }
}
