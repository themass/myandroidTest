package com.openapi.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 解析常见的代理分享链接，输出 Xray 的 outbound JSON。
 * <p>
 * 移植自开源项目 ProxyBox（DaveBugg/ProxyBox，app/.../import_config/ConfigParser.kt）。
 * 支持：vless://、vmess://、ss://、trojan://、hy2:// / hysteria2://。
 * 输出的 Xray 格式 outbound 再由 SingBoxConfigBuilder 翻译成 sing-box，因此不需要为每种协议单独写引擎。
 * 仅依赖 java.* 与 Gson，可以直接在电脑上做单元测试。
 */
public final class ShareLinkParser {

    private ShareLinkParser() {
    }

    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    /** 解析结果。protocol 是 Xray 协议名：vless / vmess / shadowsocks / trojan / hysteria2。 */
    public static final class Result {
        @NonNull
        public final String protocol;
        @NonNull
        public final String remark;
        @NonNull
        public final JsonObject outbound;

        Result(@NonNull String protocol, @NonNull String remark, @NonNull JsonObject outbound) {
            this.protocol = protocol;
            this.remark = remark;
            this.outbound = outbound;
        }
    }

    @NonNull
    public static Result parse(@NonNull String input) throws ParseException {
        String s = input.trim();
        try {
            if (s.startsWith("vless://")) {
                return parseVless(s);
            } else if (s.startsWith("vmess://")) {
                return parseVmess(s);
            } else if (s.startsWith("ss://")) {
                return parseShadowsocks(s);
            } else if (s.startsWith("trojan://")) {
                return parseTrojan(s);
            } else if (s.startsWith("hy2://") || s.startsWith("hysteria2://")) {
                return parseHysteria2(s);
            }
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("链接格式错误: " + e.getMessage());
        }
        throw new ParseException("不支持的链接类型");
    }

    // ---------------------------------------------------------------- VLESS

    private static Result parseVless(String uri) throws Exception {
        URI u = new URI(uri);
        String uuid = required(u.getUserInfo(), "缺少 UUID");
        String host = required(u.getHost(), "缺少服务器地址");
        int port = u.getPort() > 0 ? u.getPort() : 443;
        Map<String, String> q = parseQuery(u.getRawQuery());

        JsonObject user = new JsonObject();
        user.addProperty("id", uuid);
        user.addProperty("encryption", "none");
        String flow = q.get("flow");
        if (flow != null && !flow.isEmpty()) {
            user.addProperty("flow", flow);
        }
        JsonObject vnext = server(host, port);
        vnext.add("users", array(user));

        JsonObject outbound = new JsonObject();
        outbound.addProperty("protocol", "vless");
        outbound.addProperty("tag", "proxy");
        JsonObject settings = new JsonObject();
        settings.add("vnext", array(vnext));
        outbound.add("settings", settings);
        String sni = q.getOrDefault("sni", host);
        outbound.add("streamSettings", buildStreamSettings(
                q.getOrDefault("type", "tcp"), q.getOrDefault("security", "none"), sni,
                q.getOrDefault("pbk", ""), q.getOrDefault("sid", ""),
                q.getOrDefault("fp", "chrome"), q.getOrDefault("headerType", "none"),
                q.getOrDefault("path", "/"), q.getOrDefault("host", sni),
                q.getOrDefault("serviceName", ""), q.getOrDefault("alpn", ""),
                "", "", false));
        return new Result("vless", remark(u, host), outbound);
    }

    // ---------------------------------------------------------------- VMESS

    private static Result parseVmess(String uri) throws Exception {
        JsonObject obj = JsonParser.parseString(decodeBase64(uri.substring("vmess://".length())))
                .getAsJsonObject();
        String host = str(obj, "add", "");
        int port = intOf(obj, "port", 443);
        String sni = str(obj, "sni", "");
        if (sni.isEmpty()) {
            sni = host;
        }

        JsonObject user = new JsonObject();
        user.addProperty("id", str(obj, "id", ""));
        user.addProperty("alterId", intOf(obj, "aid", 0));
        user.addProperty("security", "auto");
        JsonObject vnext = server(host, port);
        vnext.add("users", array(user));

        JsonObject outbound = new JsonObject();
        outbound.addProperty("protocol", "vmess");
        outbound.addProperty("tag", "proxy");
        JsonObject settings = new JsonObject();
        settings.add("vnext", array(vnext));
        outbound.add("settings", settings);
        String network = str(obj, "net", "tcp");
        String security = "tls".equals(str(obj, "tls", "")) ? "tls" : "none";
        outbound.add("streamSettings", buildStreamSettings(network, security, sni, "", "",
                str(obj, "fp", "chrome"), "none",
                str(obj, "path", "/"), str(obj, "host", sni),
                "", str(obj, "alpn", ""), "", "", false));
        return new Result("vmess", str(obj, "ps", host), outbound);
    }

    // ---------------------------------------------------------------- SHADOWSOCKS

    private static Result parseShadowsocks(String uri) throws Exception {
        String withoutScheme = uri.substring("ss://".length());
        int hash = withoutScheme.indexOf('#');
        String fragment = hash >= 0 ? withoutScheme.substring(hash + 1) : "";
        String body = hash >= 0 ? withoutScheme.substring(0, hash) : withoutScheme;

        String credentials;
        String hostPort;
        if (body.contains("@")) {
            int at = body.lastIndexOf('@');
            credentials = decodeBase64OrRaw(body.substring(0, at));
            hostPort = body.substring(at + 1);
            if (hostPort.contains("?")) {
                hostPort = hostPort.substring(0, hostPort.indexOf('?'));
            }
        } else {
            String decoded = decodeBase64(body);
            int at = decoded.lastIndexOf('@');
            credentials = decoded.substring(0, at);
            hostPort = decoded.substring(at + 1);
        }
        int colon = credentials.indexOf(':');
        String method = colon > 0 ? credentials.substring(0, colon) : "aes-256-gcm";
        String password = colon > 0 ? credentials.substring(colon + 1) : credentials;
        String host = hostPort.substring(0, hostPort.lastIndexOf(':'));
        int port = parsePort(hostPort.substring(hostPort.lastIndexOf(':') + 1), 443);

        JsonObject server = server(host, port);
        server.addProperty("method", method);
        server.addProperty("password", password);
        JsonObject outbound = new JsonObject();
        outbound.addProperty("protocol", "shadowsocks");
        outbound.addProperty("tag", "proxy");
        JsonObject settings = new JsonObject();
        settings.add("servers", array(server));
        outbound.add("settings", settings);
        String remark = fragment.isEmpty() ? host : decodeUrl(fragment);
        return new Result("shadowsocks", remark, outbound);
    }

    // ---------------------------------------------------------------- TROJAN

    private static Result parseTrojan(String uri) throws Exception {
        URI u = new URI(uri);
        String password = required(u.getUserInfo(), "缺少密码");
        String host = required(u.getHost(), "缺少服务器地址");
        int port = u.getPort() > 0 ? u.getPort() : 443;
        Map<String, String> q = parseQuery(u.getRawQuery());
        String sni = q.getOrDefault("sni", host);

        JsonObject server = server(host, port);
        server.addProperty("password", password);
        JsonObject outbound = new JsonObject();
        outbound.addProperty("protocol", "trojan");
        outbound.addProperty("tag", "proxy");
        JsonObject settings = new JsonObject();
        settings.add("servers", array(server));
        outbound.add("settings", settings);
        outbound.add("streamSettings", buildStreamSettings(
                q.getOrDefault("type", "tcp"), q.getOrDefault("security", "tls"), sni, "", "",
                q.getOrDefault("fp", "chrome"), "none",
                q.getOrDefault("path", "/"), q.getOrDefault("host", sni),
                q.getOrDefault("serviceName", ""), q.getOrDefault("alpn", ""),
                q.getOrDefault("authority", ""), q.getOrDefault("mode", ""),
                "1".equals(q.get("allowInsecure")) || "1".equals(q.get("insecure"))));
        return new Result("trojan", remark(u, host), outbound);
    }

    // ---------------------------------------------------------------- HYSTERIA2

    private static Result parseHysteria2(String uri) throws Exception {
        URI u = new URI(uri.replace("hysteria2://", "hy2://"));
        String auth = required(u.getUserInfo(), "缺少认证密码");
        String host = required(u.getHost(), "缺少服务器地址");
        int port = u.getPort() > 0 ? u.getPort() : 443;
        Map<String, String> q = parseQuery(u.getRawQuery());

        JsonObject server = server(host, port);
        server.addProperty("password", auth);
        JsonObject tls = new JsonObject();
        tls.addProperty("serverName", q.getOrDefault("sni", host));
        if ("1".equals(q.get("insecure"))) {
            tls.addProperty("allowInsecure", true);
        }
        JsonObject stream = new JsonObject();
        stream.addProperty("network", "tcp");
        stream.addProperty("security", "tls");
        stream.add("tlsSettings", tls);

        JsonObject outbound = new JsonObject();
        outbound.addProperty("protocol", "hysteria2");
        outbound.addProperty("tag", "proxy");
        JsonObject settings = new JsonObject();
        settings.add("servers", array(server));
        outbound.add("settings", settings);
        outbound.add("streamSettings", stream);
        return new Result("hysteria2", remark(u, host), outbound);
    }

    // ---------------------------------------------------------------- shared

    /** 与 ProxyBox 的 buildStreamSettings 对齐：按 security 与 network 生成 streamSettings。 */
    private static JsonObject buildStreamSettings(String network, String security, String sni,
                                                  String pbk, String sid, String fp,
                                                  String headerType, String path, String wsHost,
                                                  String serviceName, String alpn,
                                                  String authority, String grpcMode,
                                                  boolean allowInsecure) {
        JsonObject ss = new JsonObject();
        ss.addProperty("network", network);
        switch (security) {
            case "reality": {
                ss.addProperty("security", "reality");
                JsonObject reality = new JsonObject();
                reality.addProperty("serverName", sni);
                reality.addProperty("fingerprint", fp);
                reality.addProperty("publicKey", pbk);
                reality.addProperty("shortId", sid);
                ss.add("realitySettings", reality);
                break;
            }
            case "tls": {
                ss.addProperty("security", "tls");
                JsonObject tls = new JsonObject();
                tls.addProperty("serverName", sni);
                if (!fp.isEmpty()) {
                    tls.addProperty("fingerprint", fp);
                }
                if (allowInsecure) {
                    tls.addProperty("allowInsecure", true);
                }
                String effectiveAlpn = !alpn.isEmpty() ? alpn
                        : ("grpc".equals(network) || "h2".equals(network)) ? "h2" : "";
                if (!effectiveAlpn.isEmpty()) {
                    JsonArray list = new JsonArray();
                    for (String a : effectiveAlpn.split(",")) {
                        list.add(a);
                    }
                    tls.add("alpn", list);
                }
                ss.add("tlsSettings", tls);
                break;
            }
            default:
                ss.addProperty("security", "none");
        }

        switch (network) {
            case "ws": {
                JsonObject ws = new JsonObject();
                ws.addProperty("path", path);
                if (!wsHost.isEmpty()) {
                    JsonObject headers = new JsonObject();
                    headers.addProperty("Host", wsHost);
                    ws.add("headers", headers);
                }
                ss.add("wsSettings", ws);
                break;
            }
            case "grpc": {
                JsonObject grpc = new JsonObject();
                grpc.addProperty("serviceName", serviceName);
                grpc.addProperty("multiMode", "multi".equals(grpcMode));
                if (!authority.isEmpty()) {
                    grpc.addProperty("authority", authority);
                }
                ss.add("grpcSettings", grpc);
                break;
            }
            case "h2":
            case "http": {
                JsonObject http = new JsonObject();
                http.addProperty("path", path);
                if (!wsHost.isEmpty()) {
                    http.add("host", array(wsHost));
                }
                ss.add("httpSettings", http);
                break;
            }
            case "httpupgrade": {
                JsonObject hu = new JsonObject();
                hu.addProperty("path", path);
                if (!wsHost.isEmpty()) {
                    hu.addProperty("host", wsHost);
                }
                ss.add("httpupgradeSettings", hu);
                break;
            }
            case "splithttp":
            case "xhttp": {
                JsonObject sh = new JsonObject();
                sh.addProperty("path", path);
                if (!wsHost.isEmpty()) {
                    sh.addProperty("host", wsHost);
                }
                ss.add("splithttpSettings", sh);
                break;
            }
            case "tcp": {
                if ("http".equals(headerType)) {
                    JsonObject request = new JsonObject();
                    request.addProperty("path", path);
                    if (!wsHost.isEmpty()) {
                        JsonObject headers = new JsonObject();
                        headers.add("Host", array(wsHost));
                        request.add("headers", headers);
                    }
                    JsonObject header = new JsonObject();
                    header.addProperty("type", "http");
                    header.add("request", request);
                    JsonObject tcp = new JsonObject();
                    tcp.add("header", header);
                    ss.add("tcpSettings", tcp);
                }
                break;
            }
            default:
                break;
        }
        return ss;
    }

    @NonNull
    private static JsonObject server(@NonNull String host, int port) {
        JsonObject s = new JsonObject();
        s.addProperty("address", host);
        s.addProperty("port", port);
        return s;
    }

    @NonNull
    private static JsonArray array(@NonNull JsonElement element) {
        JsonArray a = new JsonArray();
        a.add(element);
        return a;
    }

    @NonNull
    private static JsonArray array(@NonNull String value) {
        JsonArray a = new JsonArray();
        a.add(value);
        return a;
    }

    @NonNull
    private static String str(@NonNull JsonObject obj, @NonNull String key, @NonNull String fallback) {
        JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            return fallback;
        }
        return e.getAsString();
    }

    private static int intOf(@NonNull JsonObject obj, @NonNull String key, int fallback) {
        return parsePort(str(obj, key, null), fallback);
    }

    @NonNull
    private static String remark(@NonNull URI u, @NonNull String fallback) {
        String frag = u.getRawFragment();
        return frag == null || frag.isEmpty() ? fallback : decodeUrl(frag);
    }

    @NonNull
    private static String required(@Nullable String value, @NonNull String message) throws ParseException {
        if (value == null || value.trim().isEmpty()) {
            throw new ParseException(message);
        }
        return value;
    }

    private static int parsePort(@Nullable String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @NonNull
    private static Map<String, String> parseQuery(@Nullable String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            result.put(pair.substring(0, eq), decodeUrl(pair.substring(eq + 1)));
        }
        return result;
    }

    @NonNull
    private static String decodeUrl(@NonNull String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }

    /** 分享链接里的 base64 既可能是 URL-safe 也可能是标准字母表，且常缺 padding。 */
    @NonNull
    static String decodeBase64(@NonNull String input) throws ParseException {
        String padded = input.trim();
        int rem = padded.length() % 4;
        if (rem != 0) {
            padded = padded + "====".substring(rem);
        }
        try {
            return new String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            try {
                return new String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e2) {
                throw new ParseException("base64 解码失败");
            }
        }
    }

    @NonNull
    private static String decodeBase64OrRaw(@NonNull String input) {
        try {
            return decodeBase64(input);
        } catch (ParseException e) {
            return input;
        }
    }
}
