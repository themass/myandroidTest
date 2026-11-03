package com.openapi.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.openapi.data.remote.ShareLinkParser;
import com.openapi.domain.repository.TunnelRequest;

/**
 * 用户手工粘贴的分享链接节点（vless / vmess / ss / trojan / hy2）。
 * <p>
 * 与 IKEv2/WireGuard 不同，这条通道目前没有走本项目的服务端下发——用户自己的
 * 服务器通常以分享链接的形式给出（几乎所有面板都这样导出），直接粘贴解析即可用，
 * 不需要等后端支持。解析结果是完整的 Xray outbound，由 libXray 运行。
 */
public final class VlessProfileStore {

    private static final String PREFS = "vless_reality_profile";
    private static final String KEY_LINK = "link";
    private static final String KEY_REMARK = "remark";

    private VlessProfileStore() {
    }

    /**
     * 保存一条分享链接。先解析校验，解析失败不写入。
     *
     * @return 解析出的备注名（供 UI 展示）
     */
    @Nullable
    public static String save(@NonNull Context context, @NonNull String link)
            throws ShareLinkParser.ParseException {
        ShareLinkParser.Result parsed = ShareLinkParser.parse(link);
        prefs(context).edit()
                .putString(KEY_LINK, link.trim())
                .putString(KEY_REMARK, parsed.remark)
                .apply();
        return parsed.remark;
    }

    public static void clear(@NonNull Context context) {
        prefs(context).edit().clear().apply();
    }

    public static boolean hasProfile(@NonNull Context context) {
        return prefs(context).contains(KEY_LINK);
    }

    @Nullable
    public static String getRemark(@NonNull Context context) {
        return prefs(context).getString(KEY_REMARK, null);
    }

    /**
     * 取出已保存链接并解析成连接请求；链接损坏或未保存时返回 null。
     * <p>
     * 不缓存解析结果——链接本身就是唯一事实来源，每次都重新解析，
     * 避免"保存的链接和内存里用的参数不一致"这类问题。
     */
    @Nullable
    public static TunnelRequest.Builder buildRequest(@NonNull Context context) {
        String link = prefs(context).getString(KEY_LINK, null);
        if (link == null) {
            return null;
        }
        try {
            ShareLinkParser.Result parsed = ShareLinkParser.parse(link);
            JsonObject server = firstServer(parsed.outbound);
            return new TunnelRequest.Builder()
                    .server(server.get("address").getAsString())
                    .port(server.get("port").getAsInt())
                    .xrayOutboundJson(parsed.outbound.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /** vless/vmess 放在 vnext，ss/trojan/hy2 放在 servers；两者都取第一个节点。 */
    @NonNull
    private static JsonObject firstServer(@NonNull JsonObject outbound) {
        JsonObject settings = outbound.getAsJsonObject("settings");
        String key = settings.has("vnext") ? "vnext" : "servers";
        return settings.getAsJsonArray(key).get(0).getAsJsonObject();
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
