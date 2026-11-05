package com.openapi.data.remote;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 从官方节点下发的 {@code proxyLinks}（JSON：协议名 -> 分享链接）里挑出可以连接的候选。
 * <p>
 * 这些链接和「用户自己的节点」走同一条解析与连接路径（{@link ShareLinkParser} -> sing-box），
 * 所以新增协议不需要改后端表结构，也不需要新的客户端解析逻辑。
 * <p>
 * 本类不依赖 Android，可以直接在 JVM 单元测试里验证。
 */
public final class ProxyLinkSelector {

    /**
     * 候选顺序：TCP 系协议优先，UDP 系的 hysteria2 放最后（UDP 在受限网络里更容易被限速或封锁）。
     * 键就是节点 {@code protocols} 字段里用的协议名。
     */
    private static final String[] ORDER = {"vless-reality", "trojan", "vmess", "shadowsocks", "hysteria2"};

    private ProxyLinkSelector() {
    }

    /** 一个可连接的候选：协议名、原始链接、已解析结果。 */
    public static final class Candidate {
        @NonNull
        public final String protocol;
        @NonNull
        public final String link;
        @NonNull
        public final ShareLinkParser.Result result;

        public Candidate(@NonNull String protocol, @NonNull String link, @NonNull ShareLinkParser.Result result) {
            this.protocol = protocol;
            this.link = link;
            this.result = result;
        }
    }

    /**
     * @param protocols  节点声明的协议，逗号分隔；为空表示该节点没有声明任何代理协议
     * @param proxyLinks 协议名 -> 链接 的 JSON；为空或格式不对返回空列表
     * @return 按优先级排序的候选。节点没声明的协议、解析失败的链接、协议名与链接类型对不上的条目都会被跳过
     */
    @NonNull
    public static List<Candidate> candidates(String protocols, String proxyLinks) {
        if (isBlank(protocols) || isBlank(proxyLinks)) {
            return Collections.emptyList();
        }
        JsonObject links;
        try {
            JsonElement root = JsonParser.parseString(proxyLinks);
            if (!root.isJsonObject()) {
                return Collections.emptyList();
            }
            links = root.getAsJsonObject();
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }

        List<Candidate> out = new ArrayList<>();
        for (String protocol : ORDER) {
            if (!declares(protocols, protocol)) {
                continue;
            }
            Map.Entry<String, JsonElement> entry = find(links, protocol);
            if (entry == null || !entry.getValue().isJsonPrimitive()) {
                continue;
            }
            String link = entry.getValue().getAsString();
            try {
                ShareLinkParser.Result result = ShareLinkParser.parse(link);
                // 防止后台把 vmess 链接填进了 trojan 的键里，连接时才发现协议对不上。
                if (!expectedXrayProtocol(protocol).equals(result.protocol)
                        || result.server() == null || result.port() <= 0) {
                    continue;
                }
                out.add(new Candidate(protocol, link, result));
            } catch (ShareLinkParser.ParseException e) {
                // 单条链接坏了不影响其他协议，跳过。
            }
        }
        return out;
    }

    /** 节点 protocols 里是否声明了该协议（大小写不敏感，忽略空白）。 */
    private static boolean declares(String protocols, String protocol) {
        for (String p : protocols.split(",")) {
            if (p.trim().equalsIgnoreCase(protocol)) {
                return true;
            }
        }
        return false;
    }

    private static Map.Entry<String, JsonElement> find(JsonObject links, String protocol) {
        for (Map.Entry<String, JsonElement> e : links.entrySet()) {
            if (e.getKey().trim().equalsIgnoreCase(protocol)) {
                return e;
            }
        }
        return null;
    }

    /** 节点协议名对应 {@link ShareLinkParser.Result#protocol}（Xray 协议名）。 */
    private static String expectedXrayProtocol(String protocol) {
        return "vless-reality".equals(protocol) ? "vless" : protocol;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
