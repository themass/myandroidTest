package com.openapi.ks.myapp.translation.realtime;

import android.content.Context;

import com.openapi.common.util.PreferenceUtils;
import com.openapi.common.util.StringUtils;

/**
 * 火山豆包流式 ASR 凭证（设置页或 Preference 写入）。
 */
public final class VolcengineAsrConfig {

    // 偏好设置的 key，和原来 Constants 里的值相同，保存的配置不会丢
    static final String PREF_APP_KEY = "VOLCANO_ASR_APP_KEY";
    static final String PREF_ACCESS_KEY = "VOLCANO_ASR_ACCESS_KEY";
    static final String PREF_RESOURCE_ID = "VOLCANO_ASR_RESOURCE_ID";
    static final String PREF_CLUSTER = "VOLCANO_ASR_CLUSTER";
    static final String PREF_USE_V3 = "VOLCANO_ASR_USE_V3";

    private static final String DEFAULT_RESOURCE_ID = "volc.bigasr.sauc.duration";
    private static final String DEFAULT_CLUSTER = "volcengine_input_common";
    /** 设置页未填写时使用（可在设置中覆盖） */
    private static final String BUILTIN_APP_KEY = "4319026663";
    private static final String BUILTIN_ACCESS_KEY = "YJYBl-jGgkv-AZfQrwObHWfbwa5w3aAX";
    private static final String WS_V3 = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async";
    private static final String WS_V2 = "wss://openspeech.bytedance.com/api/v2/asr";

    public final String appKey;
    public final String accessKey;
    public final String resourceId;
    public final String cluster;
    public final boolean useV3;

    private VolcengineAsrConfig(String appKey, String accessKey, String resourceId, String cluster, boolean useV3) {
        this.appKey = appKey;
        this.accessKey = accessKey;
        this.resourceId = resourceId;
        this.cluster = cluster;
        this.useV3 = useV3;
    }

    public static VolcengineAsrConfig load(Context context) {
        String appKey = trim(PreferenceUtils.getPrefString(context, PREF_APP_KEY, ""));
        String accessKey = trim(PreferenceUtils.getPrefString(context, PREF_ACCESS_KEY, ""));
        if (!StringUtils.hasText(appKey)) {
            appKey = BUILTIN_APP_KEY;
        }
        if (!StringUtils.hasText(accessKey)) {
            accessKey = BUILTIN_ACCESS_KEY;
        }
        String resourceId = trim(PreferenceUtils.getPrefString(context, PREF_RESOURCE_ID, DEFAULT_RESOURCE_ID));
        if (!StringUtils.hasText(resourceId)) {
            resourceId = DEFAULT_RESOURCE_ID;
        }
        String cluster = trim(PreferenceUtils.getPrefString(context, PREF_CLUSTER, DEFAULT_CLUSTER));
        if (!StringUtils.hasText(cluster)) {
            cluster = DEFAULT_CLUSTER;
        }
        boolean useV3 = PreferenceUtils.getPrefBoolean(context, PREF_USE_V3, false);
        return new VolcengineAsrConfig(appKey, accessKey, resourceId, cluster, useV3);
    }

    public boolean isConfigured() {
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(accessKey)) {
            return false;
        }
        if (useV3) {
            return StringUtils.hasText(resourceId);
        }
        return StringUtils.hasText(cluster);
    }

    public String websocketUrl() {
        return useV3 ? WS_V3 : WS_V2;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
