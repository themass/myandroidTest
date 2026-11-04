package com.openapi.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.openapi.common.util.PreferenceUtils;
import com.openapi.data.remote.ShareLinkParser;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 用户自己添加的节点（粘贴分享链接得到）。只存在本机，不上传服务器。
 * 连接时由 Xray 承载，outbound 直接用解析出来的 JSON。
 */
public final class UserNodeStore {

    private static final String KEY_NODES = "USER_NODES";
    private static final String KEY_SELECTED = "USER_NODE_SELECTED";
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<UserNode>>() {
    }.getType();

    private UserNodeStore() {
    }

    /** 一个用户节点。outboundJson 是 Xray 的完整 outbound。 */
    public static final class UserNode {
        public String id;
        public String name;
        public String protocol;
        public String server;
        public int port;
        public String outboundJson;
        /** 用户粘贴的原始分享链接，编辑时回显。 */
        public String link;
    }

    /** 只接受 Xray 已支持、且本次范围内的协议：vmess、trojan。 */
    private static final List<String> ACCEPTED = java.util.Arrays.asList("vmess", "trojan");

    /**
     * 解析并保存一条分享链接。
     *
     * @throws ShareLinkParser.ParseException 格式不对或协议不支持
     */
    @NonNull
    public static UserNode addFromLink(@NonNull Context context, @NonNull String link)
            throws ShareLinkParser.ParseException {
        UserNode node = new UserNode();
        node.id = UUID.randomUUID().toString();
        fill(node, link, null);
        List<UserNode> nodes = list(context);
        nodes.add(node);
        save(context, nodes);
        return node;
    }

    /**
     * 编辑已有节点：名称可改，链接重新解析（协议、地址、端口都跟着链接变）。
     *
     * @throws ShareLinkParser.ParseException 链接格式不对或协议不支持，原节点保持不变
     */
    public static void update(@NonNull Context context, @NonNull String id, @Nullable String name,
                              @NonNull String link) throws ShareLinkParser.ParseException {
        List<UserNode> nodes = list(context);
        for (UserNode node : nodes) {
            if (id.equals(node.id)) {
                fill(node, link, name);
                save(context, nodes);
                return;
            }
        }
        throw new ShareLinkParser.ParseException("节点不存在");
    }

    /** 用链接填充节点字段；name 为空时用链接里的备注名。 */
    private static void fill(@NonNull UserNode node, @NonNull String link, @Nullable String name)
            throws ShareLinkParser.ParseException {
        ShareLinkParser.Result result = ShareLinkParser.parse(link.trim());
        if (!ACCEPTED.contains(result.protocol)) {
            throw new ShareLinkParser.ParseException("暂不支持该协议: " + result.protocol);
        }
        JsonObject address = firstServer(result.outbound);
        if (address == null) {
            throw new ShareLinkParser.ParseException("链接里没有服务器地址");
        }
        String remark = name != null && !name.trim().isEmpty() ? name.trim()
                : (result.remark.isEmpty() ? result.protocol + " node" : result.remark);
        node.name = remark;
        node.protocol = result.protocol;
        node.outboundJson = result.outbound.toString();
        node.server = address.get("address").getAsString();
        node.port = address.get("port").getAsInt();
        node.link = link.trim();
    }

    /** 用服务端列表整体覆盖本地缓存（登录后拉取）。 */
    public static void replaceAll(@NonNull Context context, @NonNull List<UserNode> nodes) {
        save(context, new ArrayList<>(nodes));
        String selected = selectedId(context);
        if (selected != null && !containsId(nodes, selected)) {
            select(context, null);
        }
    }

    /** 新增或更新一条缓存（以服务端返回为准）。 */
    public static void upsert(@NonNull Context context, @NonNull UserNode node) {
        List<UserNode> nodes = list(context);
        boolean replaced = false;
        for (int i = 0; i < nodes.size(); i++) {
            if (node.id.equals(nodes.get(i).id)) {
                nodes.set(i, node);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            nodes.add(node);
        }
        save(context, nodes);
    }

    /** 链接对应的 Xray outbound JSON；解析失败返回 null（连接时会报错）。 */
    @Nullable
    public static String outboundFromLink(@Nullable String link) {
        if (link == null || link.isEmpty()) {
            return null;
        }
        try {
            return ShareLinkParser.parse(link).outbound.toString();
        } catch (ShareLinkParser.ParseException e) {
            return null;
        }
    }

    private static boolean containsId(@NonNull List<UserNode> nodes, @NonNull String id) {
        for (UserNode node : nodes) {
            if (id.equals(node.id)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public static List<UserNode> list(@NonNull Context context) {
        String raw = PreferenceUtils.getPrefString(context, KEY_NODES, "");
        if (raw.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<UserNode> nodes = GSON.fromJson(raw, LIST_TYPE);
            return nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    public static void remove(@NonNull Context context, @NonNull String id) {
        List<UserNode> nodes = list(context);
        nodes.removeIf(n -> id.equals(n.id));
        save(context, nodes);
        if (id.equals(selectedId(context))) {
            select(context, null);
        }
    }

    /** 当前选中的用户节点；未选中（使用服务器节点）时返回 null。 */
    @Nullable
    public static UserNode selected(@NonNull Context context) {
        String id = selectedId(context);
        if (id == null) {
            return null;
        }
        for (UserNode node : list(context)) {
            if (id.equals(node.id)) {
                return node;
            }
        }
        return null;
    }

    public static void select(@NonNull Context context, @Nullable String id) {
        PreferenceUtils.setPrefString(context, KEY_SELECTED, id == null ? "" : id);
    }

    @Nullable
    private static String selectedId(@NonNull Context context) {
        String id = PreferenceUtils.getPrefString(context, KEY_SELECTED, "");
        return id.isEmpty() ? null : id;
    }

    private static void save(@NonNull Context context, @NonNull List<UserNode> nodes) {
        PreferenceUtils.setPrefString(context, KEY_NODES, GSON.toJson(nodes, LIST_TYPE));
    }

    /** vmess/vless 在 settings.vnext，trojan/shadowsocks 在 settings.servers。 */
    @Nullable
    private static JsonObject firstServer(@NonNull JsonObject outbound) {
        if (!outbound.has("settings")) {
            return null;
        }
        JsonObject settings = outbound.getAsJsonObject("settings");
        for (String key : new String[]{"vnext", "servers"}) {
            if (settings.has(key)) {
                JsonArray array = settings.getAsJsonArray(key);
                if (array.size() > 0) {
                    JsonElement first = array.get(0);
                    return first.isJsonObject() ? first.getAsJsonObject() : null;
                }
            }
        }
        return null;
    }
}
