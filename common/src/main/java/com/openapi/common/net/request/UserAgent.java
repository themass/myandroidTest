package com.openapi.common.net.request;

import android.content.Context;

import com.openapi.common.net.http.HttpUtils;
import com.openapi.common.util.android.DeviceInfoUtils;
import com.openapi.common.util.android.SystemUtils;
import com.openapi.common.util.lang.Md5;
import com.openapi.common.util.lang.StringUtils;

/**
 * 全 App User-Agent 与设备号（DEVID）的唯一生成处。
 *
 * 原先这两样长在 {@code BaseRequest} 构造器里，只有走 API 的请求才有 UA。
 * 图片/封面走 Glide 自己的 OkHttp（{@code GlideHttpClient}），请求上根本没有 UA——
 * 而 CDN 恰恰按 UA 放行、服务端密钥又取 UA 末 16 位（见 {@code BaseRequest.authkey}），
 * 于是封面时好时坏（A40）。把生成逻辑从 {@code BaseRequest} 抽到这里，
 * API 链与封面链共用同一份，别再复制粘贴两份各自漂移。
 *
 * UA 每次调用现取时间戳（签名段 {@code time + Md5(devid|time)} 与旧实现逐字一致），
 * 所以同一个进程里两次调用 UA 并不相同——相同的是身份前缀与 DEVID。
 */
public final class UserAgent {

    private static final String UA_DEFAULT = System.getProperty("http.agent", "");

    private static String appSuffix = null;
    private static String channel = null;
    private static String deviceId = null;

    private UserAgent() {
    }

    /**
     * 设备号：ANDROID_ID，拿不到（真返回字符串 "null"）时退随机 UUID。
     * 服务端只要求非空；缺了它 {@code DevApp.check()} 直接 500，别为了过校验伪造渠道。
     */
    public static synchronized String deviceId(Context context) {
        if (deviceId == null) {
            String sb = DeviceInfoUtils.getDeviceId(context);
            if (!DeviceInfoUtils.NULL.equals(sb)) {
                deviceId = sb;
            } else {
                deviceId = "androiduuid" + DeviceInfoUtils.getUUID(context);
            }
        }
        return deviceId;
    }

    /** 组装与旧 {@code BaseRequest} 逐字同款的 UA。 */
    public static String build(Context context) {
        return build(context, System.currentTimeMillis());
    }

    /** 时间戳可注入：签名段的确定性（{@code time + Md5(devid|time)}）靠它可测。 */
    static String build(Context context, long time) {
        if (appSuffix == null) {
            appSuffix = HttpUtils.getUserAgentSuffix(context);
        }
        if (!StringUtils.hasText(channel)) {
            channel = DeviceInfoUtils.getMetaData(context, "UMENG_CHANNEL");
        }
        // 渠道没配到时是 null，拼进 UA 会变成字面量 "null"（服务端日志里 channel=null
        // 看起来像真有这么个渠道）。降级成空串：宁可不带渠道，也不把 Java 的 null 当取值发出去。
        if (channel == null) {
            channel = "";
        }
        String sb = deviceId(context) + "|" + time;
        String msg = time + Md5.encode(sb);
        // Webkit/<fileTxt> 段照旧：fileTxt 自始就是 "null"（getContextId 那条从未启用）。
        return UA_DEFAULT + appSuffix + ",channel=" + channel + ",cpu=" + SystemUtils.getCpuType()
                + ",Webkit/" + DeviceInfoUtils.NULL + ",IE" + msg;
    }
}
