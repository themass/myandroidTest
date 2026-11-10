package com.openapi.core.ads.yewu.ads.config;

import com.openapi.core.ads.yewu.ads.base.AdsContext;

import kotlinx.coroutines.flow.MutableSharedFlow;
import kotlinx.coroutines.flow.SharedFlow;

/**
 * :ads 的事件（开屏广告关闭、开屏广告的下一个来源）。:ads 在 :core 之下，所以事件放在这里，
 * 而不是 :core 的 CoreEvents。订阅方在 :core / :app 里用 observeJava 收集。
 * :ads 是纯 Java 模块，因此这里用 Java 写，直接用 coroutines 的 flow 类型。
 */
public final class AdsEvents {

    private AdsEvents() {
    }

    private static final MutableSharedFlow<SplashAdDissmisEvent> splashDismissed =
            com.openapi.core.event.EventFlowsKt.eventFlow();
    private static final MutableSharedFlow<LaunchAdsNext> launchAdsNext =
            com.openapi.core.event.EventFlowsKt.eventFlow();

    public static SharedFlow<SplashAdDissmisEvent> splashDismissed() {
        return splashDismissed;
    }

    public static SharedFlow<LaunchAdsNext> launchAdsNext() {
        return launchAdsNext;
    }

    public static void postSplashDismissed(SplashAdDissmisEvent event) {
        splashDismissed.tryEmit(event);
    }

    public static void postLaunchAdsNext(AdsContext.AdsFrom from) {
        launchAdsNext.tryEmit(new LaunchAdsNext(from));
    }
}
