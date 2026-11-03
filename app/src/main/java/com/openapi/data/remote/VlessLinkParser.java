package com.openapi.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.domain.repository.TunnelRequest;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 解析标准的 {@code vless://} 分享链接。
 * <p>
 * 这个格式不是本项目定义的——几乎所有 VLESS/Reality 面板（Xray 官方工具、
 * 3x-ui、各类一键脚本）导出节点时都用这个 URI 形式，社区把它当作事实标准：
 * <pre>
 *   vless://{uuid}@{host}:{port}?encryption=none&flow=xtls-rprx-vision
 *           &security=reality&sni={serverName}&fp={fingerprint}
 *           &pbk={publicKey}&sid={shortId}&type=tcp#{remark}
 * </pre>
 * 用户直接从自己的面板复制粘贴整条链接即可，不需要手工填各个字段。
 */
public final class VlessLinkParser {

    private VlessLinkParser() {
    }

    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    /** 解析结果；remark 是链接里的备注名（# 后面那段），用于节点列表展示。 */
    public static final class ParsedLink {
        @NonNull
        public final TunnelRequest.Builder requestBuilder;
        @Nullable
        public final String remark;

        ParsedLink(@NonNull TunnelRequest.Builder requestBuilder, @Nullable String remark) {
            this.requestBuilder = requestBuilder;
            this.remark = remark;
        }
    }

    @NonNull
    public static ParsedLink parse(@NonNull String link) throws ParseException {
        String trimmed = link.trim();
        if (!trimmed.startsWith("vless://")) {
            throw new ParseException("不是 vless:// 链接");
        }
        try {
            // java.net.URI 对 userinfo 部分（UUID）解析稳定；fragment（备注）需要手动取。
            URI uri = new URI(trimmed);
            String uuid = uri.getUserInfo();
            String host = uri.getHost();
            int port = uri.getPort();
            if (uuid == null || uuid.trim().isEmpty()) {
                throw new ParseException("缺少 UUID");
            }
            if (host == null || host.trim().isEmpty()) {
                throw new ParseException("缺少服务器地址");
            }
            if (port <= 0) {
                throw new ParseException("缺少端口");
            }

            Map<String, String> params = parseQuery(uri.getRawQuery());
            String security = params.get("security");
            if (!"reality".equalsIgnoreCase(security)) {
                throw new ParseException("仅支持 security=reality，当前为: " + security);
            }

            String remark = uri.getRawFragment() != null
                    ? URLDecoder.decode(uri.getRawFragment(), "UTF-8") : null;

            String sni = firstNonNull(params.get("sni"), params.get("serverName"));
            String pbk = firstNonNull(params.get("pbk"), params.get("publicKey"));
            if (sni == null || sni.trim().isEmpty()) {
                throw new ParseException("缺少 sni（Reality 伪装域名）");
            }
            if (pbk == null || pbk.trim().isEmpty()) {
                throw new ParseException("缺少 pbk（Reality 公钥）");
            }

            TunnelRequest.Builder b = new TunnelRequest.Builder()
                    .server(host)
                    .port(port)
                    .vlessUuid(uuid)
                    // 链接没带 flow 就不猜：服务端这个用户可能压根没配 flow
                    // （纯 Reality，不带 XTLS Vision），硬塞默认值会导致和服务端
                    // 配置不匹配、握手失败。引擎侧同样只在非空时才下发 flow 字段。
                    .vlessFlow(params.get("flow"))
                    .realityServerName(sni)
                    .realityPublicKey(pbk)
                    .realityShortId(firstNonNull(params.get("sid"), params.get("shortId")))
                    .realityFingerprint(firstNonNull(params.get("fp"), params.get("fingerprint")));

            return new ParsedLink(b, remark);
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("链接格式错误: " + e.getMessage());
        }
    }

    @NonNull
    private static Map<String, String> parseQuery(@Nullable String rawQuery) throws Exception {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = pair.substring(0, eq);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8.name());
            result.put(key, value);
        }
        return result;
    }

    @Nullable
    private static String firstNonNull(@Nullable String a, @Nullable String b) {
        return a != null ? a : b;
    }
}
