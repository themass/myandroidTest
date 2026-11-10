package com.openapi.data.vpn.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 把 Xray 格式的 outbound（{@link com.openapi.data.remote.ShareLinkParser} 的输出）
 * 翻译成 sing-box 的 outbound，并组装完整的 sing-box 配置。
 * <p>
 * 这里只做协议字段映射，不碰网络。所有无法表达的输入（比如 XHTTP 传输）都抛
 * {@link IllegalArgumentException}，由引擎转成 CONFIG_INVALID 报给 UI，
 * 而不是静默降级成一个跑不通的配置。
 */
public final class SingBoxConfigBuilder {

    /** 代理出口的 tag；路由和 DNS 都引用它。 */
    static final String PROXY_TAG = "proxy";
    static final String DIRECT_TAG = "direct";
    static final String TUN_TAG = "tun-in";
    static final String DNS_REMOTE_TAG = "dns-remote";
    static final String DNS_DIRECT_TAG = "dns-direct";
    static final String DEFAULT_FINGERPRINT = "chrome";
    /** 隧道 MTU。1500 在封装后必然分片/丢包，统一用 IPv6 最小 MTU 1280。 */
    static final int DEFAULT_MTU = 1280;

    private static final Gson GSON = new Gson();
    private static final java.util.Set<String> SUPPORTED_PROTOCOLS = new java.util.HashSet<>(
            java.util.Arrays.asList("vless", "vmess", "trojan", "shadowsocks", "hysteria2"));

    private SingBoxConfigBuilder() {
    }

    /**
     * 把 Xray outbound 转成 sing-box outbound，tag 固定为 {@link #PROXY_TAG}。
     *
     * @throws IllegalArgumentException 协议或传输不被 sing-box 支持，或缺少必要字段
     */
    @NonNull
    public static JsonObject convertOutbound(@NonNull JsonObject xray) {
        String protocol = str(xray, "protocol", "");
        if (!SUPPORTED_PROTOCOLS.contains(protocol)) {
            throw new IllegalArgumentException("sing-box 不支持的协议: " + protocol);
        }
        JsonObject endpoint = endpoint(xray, protocol);
        JsonObject out = new JsonObject();
        out.addProperty("tag", PROXY_TAG);
        out.addProperty("server", str(endpoint, "address", ""));
        out.addProperty("server_port", intOf(endpoint, "port", 0));
        switch (protocol) {
            case "vless": {
                JsonObject user = firstUser(endpoint);
                out.addProperty("type", "vless");
                out.addProperty("uuid", str(user, "id", ""));
                String flow = str(user, "flow", "");
                if (!flow.isEmpty()) {
                    out.addProperty("flow", flow);
                }
                break;
            }
            case "vmess": {
                JsonObject user = firstUser(endpoint);
                out.addProperty("type", "vmess");
                out.addProperty("uuid", str(user, "id", ""));
                out.addProperty("alter_id", intOf(user, "alterId", 0));
                out.addProperty("security", str(user, "security", "auto"));
                break;
            }
            case "trojan":
                out.addProperty("type", "trojan");
                out.addProperty("password", str(endpoint, "password", ""));
                break;
            case "shadowsocks":
                out.addProperty("type", "shadowsocks");
                out.addProperty("method", str(endpoint, "method", ""));
                out.addProperty("password", str(endpoint, "password", ""));
                break;
            case "hysteria2":
                out.addProperty("type", "hysteria2");
                out.addProperty("password", str(endpoint, "password", ""));
                break;
            default:
                throw new IllegalArgumentException("sing-box 不支持的协议: " + protocol);
        }
        applyStream(out, obj(xray, "streamSettings"));
        return out;
    }

    /**
     * 组装完整配置：tun 入站接管系统流量，代理出口走 {@code outbound}，DNS 经代理解析。
     * <p>
     * 地址、MTU、DNS 都由配置决定，{@link SingBoxVpnService#openTun} 读的是同一份
     * 数据，两边不会对不上。
     */
    @NonNull
    public static String buildConfig(@NonNull JsonObject proxyOutbound, @NonNull String tunAddress,
                                     int mtu, @NonNull String dns) {
        String firstDns = dns.split(",")[0].trim();

        JsonArray dnsServers = new JsonArray();
        dnsServers.add(dnsServer(DNS_REMOTE_TAG, firstDns, PROXY_TAG));
        dnsServers.add(dnsServer(DNS_DIRECT_TAG, firstDns, null));
        JsonObject dnsConfig = new JsonObject();
        dnsConfig.add("servers", dnsServers);
        dnsConfig.addProperty("final", DNS_REMOTE_TAG);

        JsonObject tun = new JsonObject();
        tun.addProperty("type", "tun");
        tun.addProperty("tag", TUN_TAG);
        JsonArray addresses = new JsonArray();
        addresses.add(tunAddress);
        tun.add("address", addresses);
        tun.addProperty("mtu", mtu > 0 ? mtu : DEFAULT_MTU);
        tun.addProperty("auto_route", true);
        tun.addProperty("strict_route", false);
        tun.addProperty("stack", "mixed");
        JsonArray inbounds = new JsonArray();
        inbounds.add(tun);

        JsonArray hijack = new JsonArray();
        JsonObject sniff = new JsonObject();
        sniff.addProperty("action", "sniff");
        hijack.add(sniff);
        JsonObject dnsHijack = new JsonObject();
        dnsHijack.addProperty("protocol", "dns");
        dnsHijack.addProperty("action", "hijack-dns");
        hijack.add(dnsHijack);

        JsonObject route = new JsonObject();
        route.add("rules", hijack);
        route.addProperty("final", PROXY_TAG);
        route.addProperty("auto_detect_interface", true);
        route.addProperty("default_domain_resolver", DNS_DIRECT_TAG);

        JsonObject direct = new JsonObject();
        direct.addProperty("type", "direct");
        direct.addProperty("tag", DIRECT_TAG);
        JsonArray outbounds = new JsonArray();
        outbounds.add(proxyOutbound);
        outbounds.add(direct);

        JsonObject log = new JsonObject();
        log.addProperty("level", "info");

        JsonObject root = new JsonObject();
        root.add("log", log);
        root.add("dns", dnsConfig);
        root.add("inbounds", inbounds);
        root.add("outbounds", outbounds);
        root.add("route", route);
        return GSON.toJson(root);
    }

    private static JsonObject dnsServer(String tag, String address, @Nullable String detour) {
        JsonObject server = new JsonObject();
        server.addProperty("tag", tag);
        server.addProperty("type", "udp");
        server.addProperty("server", address);
        if (detour != null) {
            server.addProperty("detour", detour);
        }
        return server;
    }

    /** vmess/vless 在 settings.vnext，trojan/shadowsocks/hysteria2 在 settings.servers。 */
    @NonNull
    private static JsonObject endpoint(@NonNull JsonObject xray, @NonNull String protocol) {
        JsonObject settings = obj(xray, "settings");
        if (settings == null) {
            throw new IllegalArgumentException("缺少 settings");
        }
        boolean vnext = "vless".equals(protocol) || "vmess".equals(protocol);
        JsonArray list = arr(settings, vnext ? "vnext" : "servers");
        if (list == null || list.size() == 0 || !list.get(0).isJsonObject()) {
            throw new IllegalArgumentException("缺少服务器地址");
        }
        return list.get(0).getAsJsonObject();
    }

    @NonNull
    private static JsonObject firstUser(@NonNull JsonObject endpoint) {
        JsonArray users = arr(endpoint, "users");
        if (users == null || users.size() == 0 || !users.get(0).isJsonObject()) {
            throw new IllegalArgumentException("缺少用户信息");
        }
        return users.get(0).getAsJsonObject();
    }

    /** 安全层 + 传输层。stream 为 null 表示纯 TCP、无加密层（shadowsocks 就是这样）。 */
    private static void applyStream(@NonNull JsonObject out, @Nullable JsonObject stream) {
        if (stream == null) {
            return;
        }
        String security = str(stream, "security", "none");
        switch (security) {
            case "reality":
                out.add("tls", realityTls(obj(stream, "realitySettings")));
                break;
            case "tls":
                out.add("tls", tls(obj(stream, "tlsSettings")));
                break;
            case "none":
                break;
            default:
                throw new IllegalArgumentException("sing-box 不支持的安全层: " + security);
        }
        JsonObject transport = transport(str(stream, "network", "tcp"), stream);
        if (transport != null) {
            out.add("transport", transport);
        }
    }

    @NonNull
    private static JsonObject tls(@Nullable JsonObject in) {
        JsonObject tls = new JsonObject();
        tls.addProperty("enabled", true);
        if (in == null) {
            return tls;
        }
        String serverName = str(in, "serverName", "");
        if (!serverName.isEmpty()) {
            tls.addProperty("server_name", serverName);
        }
        if (bool(in, "allowInsecure")) {
            tls.addProperty("insecure", true);
        }
        JsonArray alpn = arr(in, "alpn");
        if (alpn != null && alpn.size() > 0) {
            tls.add("alpn", alpn);
        }
        String fingerprint = str(in, "fingerprint", "");
        if (!fingerprint.isEmpty()) {
            tls.add("utls", utls(fingerprint));
        }
        return tls;
    }

    /** Reality 必须同时开 uTLS，sing-box 不接受没有指纹的 Reality 配置。 */
    @NonNull
    private static JsonObject realityTls(@Nullable JsonObject in) {
        if (in == null) {
            throw new IllegalArgumentException("缺少 realitySettings");
        }
        JsonObject tls = new JsonObject();
        tls.addProperty("enabled", true);
        tls.addProperty("server_name", str(in, "serverName", ""));
        tls.add("utls", utls(str(in, "fingerprint", DEFAULT_FINGERPRINT)));
        JsonObject reality = new JsonObject();
        reality.addProperty("enabled", true);
        reality.addProperty("public_key", str(in, "publicKey", ""));
        reality.addProperty("short_id", str(in, "shortId", ""));
        tls.add("reality", reality);
        return tls;
    }

    private static JsonObject utls(String fingerprint) {
        JsonObject utls = new JsonObject();
        utls.addProperty("enabled", true);
        utls.addProperty("fingerprint", fingerprint);
        return utls;
    }

    /**
     * 传输层映射。TCP 不加 transport（sing-box 默认就是裸 TCP）；
     * TCP 的 HTTP 伪装头 sing-box 没有对应项，拒绝而不是丢掉伪装。
     */
    @Nullable
    private static JsonObject transport(String network, @NonNull JsonObject stream) {
        switch (network) {
            case "tcp": {
                JsonObject tcp = obj(stream, "tcpSettings");
                JsonObject header = tcp == null ? null : obj(tcp, "header");
                if (header != null && !"none".equals(str(header, "type", "none"))) {
                    throw new IllegalArgumentException("sing-box 不支持 TCP HTTP 伪装");
                }
                return null;
            }
            case "ws": {
                JsonObject ws = obj(stream, "wsSettings");
                JsonObject t = new JsonObject();
                t.addProperty("type", "ws");
                t.addProperty("path", str(ws, "path", "/"));
                JsonObject headers = ws == null ? null : obj(ws, "headers");
                String host = headers == null ? "" : str(headers, "Host", "");
                if (!host.isEmpty()) {
                    JsonObject h = new JsonObject();
                    h.addProperty("Host", host);
                    t.add("headers", h);
                }
                return t;
            }
            case "grpc": {
                JsonObject grpc = obj(stream, "grpcSettings");
                JsonObject t = new JsonObject();
                t.addProperty("type", "grpc");
                t.addProperty("service_name", str(grpc, "serviceName", ""));
                return t;
            }
            case "h2":
            case "http": {
                JsonObject http = obj(stream, "httpSettings");
                JsonObject t = new JsonObject();
                t.addProperty("type", "http");
                t.addProperty("path", str(http, "path", "/"));
                JsonArray host = http == null ? null : arr(http, "host");
                if (host != null && host.size() > 0) {
                    t.add("host", host);
                }
                return t;
            }
            case "httpupgrade": {
                JsonObject hu = obj(stream, "httpupgradeSettings");
                JsonObject t = new JsonObject();
                t.addProperty("type", "httpupgrade");
                t.addProperty("path", str(hu, "path", "/"));
                String host = str(hu, "host", "");
                if (!host.isEmpty()) {
                    t.addProperty("host", host);
                }
                return t;
            }
            default:
                // splithttp（XHTTP）等 sing-box 没有对应实现的传输，明确拒绝。
                throw new IllegalArgumentException("sing-box 不支持的传输: " + network);
        }
    }

    private static String str(@Nullable JsonObject o, String key, String def) {
        if (o == null || !o.has(key) || !o.get(key).isJsonPrimitive()) {
            return def;
        }
        return o.get(key).getAsString();
    }

    private static int intOf(@Nullable JsonObject o, String key, int def) {
        if (o == null || !o.has(key) || !o.get(key).isJsonPrimitive()) {
            return def;
        }
        return o.get(key).getAsInt();
    }

    private static boolean bool(@Nullable JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive()
                && o.get(key).getAsBoolean();
    }

    @Nullable
    private static JsonObject obj(@Nullable JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonObject()
                ? o.getAsJsonObject(key) : null;
    }

    @Nullable
    private static JsonArray arr(@Nullable JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonArray()
                ? o.getAsJsonArray(key) : null;
    }
}
