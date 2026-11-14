package com.openapi.yewu.ads.admob;

import android.app.Activity;
import android.content.Context;

import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;
import com.openapi.common.util.LogUtil;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Google UMP（User Messaging Platform）同意管理。
 * <p>
 * 为什么必须有：AdMob 对 EEA / 英国 / 瑞士用户要求先取得同意才能使用广告 ID 做个性化广告，
 * 否则会限制广告投放；同时 Play 的 User Data 政策把"未经同意收集广告标识符"列为违规。
 * UMP 是 Google 官方组件，随 play-services-ads 一起提供，不需要额外依赖。
 * <p>
 * 流程（官方推荐的顺序，不能颠倒）：
 * <ol>
 *   <li>requestConsentInfoUpdate —— 拉取该用户所在地区是否需要同意</li>
 *   <li>consentInformation.isConsentFormAvailable() —— 表单是否已就绪</li>
 *   <li>loadAndShowConsentFormIfRequired —— 只在需要时展示，已同意过的用户不会被打扰</li>
 * </ol>
 * 广告请求必须放在同意结果回来之后，否则第一次启动可能带着未授权的 ID 发请求。
 */
public final class AdConsent {

    private static final String TAG = "AdConsent";

    /** 同意流程只跑一次；Activity 重建时不要重复拉表单。 */
    private static final AtomicBoolean started = new AtomicBoolean(false);

    /** 同意流程结束后要执行的动作（通常是初始化广告或跳转首页）。 */
    private static volatile Runnable onSettled;

    private AdConsent() {
    }

    /**
     * 发起同意流程。需要在主线程调用。
     *
     * @param context 任意 Context，内部会取 applicationContext 之外的 Activity 时自动降级
     */
    public static void requestIfNeeded(Context context) {
        if (context == null) {
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        final Context app = context.getApplicationContext();
        ConsentRequestParameters.Builder builder = new ConsentRequestParameters.Builder();

        // 调试用：正式包不要打开设备级测试，否则会强制当作 EEA 用户
        if (isDebug(context)) {
            ConsentDebugSettings debug = new ConsentDebugSettings.Builder(app)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .build();
            builder.setConsentDebugSettings(debug);
        }

        ConsentInformation info = UserMessagingPlatform.getConsentInformation(app);
        info.requestConsentInfoUpdate(
                asActivity(app),
                builder.build(),
                () -> {
                    // 已经取得过同意（或不需要同意）时，form 不可用，直接结束
                    if (!UserMessagingPlatform.getConsentInformation(app).isConsentFormAvailable()) {
                        LogUtil.i(TAG + ": 无需展示同意表单");
                        settle();
                        return;
                    }
                    loadAndShow(app);
                },
                formError -> {
                    // 拉取失败（离线、超时）不阻塞启动：按"未同意"处理，
                    // 后续广告请求会拿到非个性化广告，属于安全降级
                    LogUtil.i(TAG + ": 拉取同意信息失败 " + describe(formError));
                    settle();
                });
    }

    private static void loadAndShow(final Context app) {
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                asActivity(app),
                (FormError error) -> {
                    if (error != null) {
                        LogUtil.i(TAG + ": 展示同意表单失败 " + describe(error));
                    } else {
                        LogUtil.i(TAG + ": 同意流程完成，canRequestAds="
                                + UserMessagingPlatform.getConsentInformation(app).canRequestAds());
                    }
                    settle();
                });
    }

    /** 设置同意流程结束后的回调。必须在 requestIfNeeded 之前调用。 */
    public static void setOnSettled(Runnable action) {
        onSettled = action;
    }

    private static void settle() {
        Runnable action = onSettled;
        onSettled = null;
        if (action != null) {
            action.run();
        }
    }

    /**
     * UMP 要求 Activity 才能展示表单。Application Context 无法弹窗，
     * 这里没有合理降级——返回 null 时 SDK 会走 error 回调，流程仍能结束。
     */
    private static Activity asActivity(Context context) {
        return context instanceof Activity ? (Activity) context : null;
    }

    private static String describe(FormError error) {
        return error == null ? "null" : (error.getErrorCode() + ":" + error.getMessage());
    }

    private static boolean isDebug(Context context) {
        return (context.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
