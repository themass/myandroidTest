package com.openapi.myapp.ui.check;

import android.content.Context;

import androidx.annotation.NonNull;

import com.openapi.common.util.PreferenceUtils;

/**
 * 网络体检需要的两份本地数据：
 * <ul>
 *   <li>最近一次连接的节点地址（{@code HostVo.gateway}），用来和出口 IP 对比；</li>
 *   <li>最近一次检测到的出口位置（经纬度 + 标签），首页地球用它标记出口。</li>
 * </ul>
 * 坐标以字符串存进 SharedPreferences，和项目里其他配置保持一致。
 */
public final class ExitIpStore {

    private static final String KEY_NODE = "EXIT_NODE_GATEWAY";
    private static final String KEY_LAT = "EXIT_LAT";
    private static final String KEY_LNG = "EXIT_LNG";
    private static final String KEY_LABEL = "EXIT_LABEL";

    private ExitIpStore() {
    }

    /** 连接前调用，记录本次要连的节点地址。 */
    public static void rememberNode(@NonNull Context context, String gateway) {
        PreferenceUtils.setPrefString(context, KEY_NODE, gateway == null ? "" : gateway);
    }

    @NonNull
    public static String getNodeGateway(@NonNull Context context) {
        return PreferenceUtils.getPrefString(context, KEY_NODE, "");
    }

    public static void saveExit(@NonNull Context context, double lat, double lng, @NonNull String label) {
        PreferenceUtils.setPrefString(context, KEY_LAT, String.valueOf(lat));
        PreferenceUtils.setPrefString(context, KEY_LNG, String.valueOf(lng));
        PreferenceUtils.setPrefString(context, KEY_LABEL, label);
    }

    /** 没有保存过出口位置时返回 false。 */
    public static boolean hasExit(@NonNull Context context) {
        return !PreferenceUtils.getPrefString(context, KEY_LAT, "").isEmpty()
                && !PreferenceUtils.getPrefString(context, KEY_LNG, "").isEmpty();
    }

    public static double getExitLat(@NonNull Context context) {
        return Double.parseDouble(PreferenceUtils.getPrefString(context, KEY_LAT, "0"));
    }

    public static double getExitLng(@NonNull Context context) {
        return Double.parseDouble(PreferenceUtils.getPrefString(context, KEY_LNG, "0"));
    }

    @NonNull
    public static String getExitLabel(@NonNull Context context) {
        return PreferenceUtils.getPrefString(context, KEY_LABEL, "");
    }
}
