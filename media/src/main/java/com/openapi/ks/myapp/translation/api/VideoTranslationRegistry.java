package com.openapi.ks.myapp.translation.api;

import android.os.Handler;
import android.os.Looper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 全局注册多种 {@link VideoTranslationProvider}，并按 {@link #setActiveProviderId(String)} 切换当前实现。
 */
public final class VideoTranslationRegistry {

    private static final VideoTranslationRegistry INSTANCE = new VideoTranslationRegistry();

    public static VideoTranslationRegistry getInstance() {
        return INSTANCE;
    }

    private final Map<String, VideoTranslationProvider> providers = new ConcurrentHashMap<>();
    private volatile String activeProviderId;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private VideoTranslationRegistry() {
    }

    public void register(VideoTranslationProvider provider) {
        if (provider == null || provider.getProviderId() == null) {
            return;
        }
        providers.put(provider.getProviderId(), provider);
    }

    public void unregister(String providerId) {
        if (providerId != null) {
            providers.remove(providerId);
        }
    }

    public void setActiveProviderId(String providerId) {
        this.activeProviderId = providerId;
    }

    public String getActiveProviderId() {
        return activeProviderId;
    }

    public VideoTranslationProvider getActiveProvider() {
        if (activeProviderId == null) {
            return null;
        }
        return providers.get(activeProviderId);
    }

    /**
     * 在后台线程调用当前 Provider，回调始终切回主线程。
     */
    public void translate(VideoTranslationRequest request, VideoTranslationCallback userCallback) {
        final VideoTranslationProvider p = getActiveProvider();
        if (p == null) {
            postMain(() -> userCallback.onError("未选择翻译服务，请先注册并 setActiveProviderId"));
            return;
        }
        VideoTranslationCallback mainSafe = new VideoTranslationCallback() {
            @Override
            public void onSuccess(VideoTranslationResult result) {
                postMain(() -> userCallback.onSuccess(result));
            }

            @Override
            public void onError(String message) {
                postMain(() -> userCallback.onError(message));
            }
        };
        executor.execute(() -> {
            try {
                p.translate(request, mainSafe);
            } catch (Throwable t) {
                mainSafe.onError(t.getMessage() != null ? t.getMessage() : "translate failed");
            }
        });
    }

    private void postMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            mainHandler.post(r);
        }
    }
}
