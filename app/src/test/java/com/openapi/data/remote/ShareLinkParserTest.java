package com.openapi.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ShareLinkParserTest {

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject firstServer(JsonObject outbound, String key) {
        return outbound.getAsJsonObject("settings").getAsJsonArray(key).get(0).getAsJsonObject();
    }

    @Test
    public void vlessRealityExtractsServerAndRealitySettings() throws Exception {
        ShareLinkParser.Result r = ShareLinkParser.parse(
                "vless://11111111-2222-3333-4444-555555555555@203.0.113.10:443"
                        + "?encryption=none&type=tcp&security=reality&sni=www.example.com"
                        + "&fp=chrome&pbk=PUBKEY&sid=abcd1234#My%20Node");

        assertEquals("vless", r.protocol);
        assertEquals("My Node", r.remark);
        JsonObject user = firstServer(r.outbound, "vnext").getAsJsonArray("users").get(0).getAsJsonObject();
        assertEquals("11111111-2222-3333-4444-555555555555", user.get("id").getAsString());
        assertEquals("reality", r.outbound.getAsJsonObject("streamSettings").get("security").getAsString());
        JsonObject reality = r.outbound.getAsJsonObject("streamSettings").getAsJsonObject("realitySettings");
        assertEquals("www.example.com", reality.get("serverName").getAsString());
        assertEquals("PUBKEY", reality.get("publicKey").getAsString());
        assertEquals("abcd1234", reality.get("shortId").getAsString());
    }

    @Test
    public void vlessWithoutFlowDoesNotInventOne() throws Exception {
        ShareLinkParser.Result r = ShareLinkParser.parse(
                "vless://u@203.0.113.10:443?security=reality&sni=a.com&pbk=P&type=tcp");
        JsonObject user = firstServer(r.outbound, "vnext").getAsJsonArray("users").get(0).getAsJsonObject();
        assertTrue("flow 未配置时不能自作主张添加", !user.has("flow"));
    }

    @Test
    public void vmessDecodesBase64Json() throws Exception {
        String json = "{\"v\":\"2\",\"ps\":\"JP VMess\",\"add\":\"198.51.100.7\",\"port\":\"8443\","
                + "\"id\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\",\"aid\":\"0\",\"net\":\"ws\","
                + "\"tls\":\"tls\",\"sni\":\"cdn.example.com\",\"path\":\"/ws\",\"host\":\"cdn.example.com\"}";
        ShareLinkParser.Result r = ShareLinkParser.parse("vmess://" + b64(json));

        assertEquals("vmess", r.protocol);
        assertEquals("JP VMess", r.remark);
        assertEquals(8443, firstServer(r.outbound, "vnext").get("port").getAsInt());
        assertEquals("ws", r.outbound.getAsJsonObject("streamSettings").get("network").getAsString());
        assertEquals("/ws", r.outbound.getAsJsonObject("streamSettings")
                .getAsJsonObject("wsSettings").get("path").getAsString());
    }

    @Test
    public void shadowsocksSip002() throws Exception {
        String userInfo = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("aes-256-gcm:secretpass".getBytes(StandardCharsets.UTF_8));
        ShareLinkParser.Result r = ShareLinkParser.parse("ss://" + userInfo + "@192.0.2.5:8388#SG%20SS");

        assertEquals("shadowsocks", r.protocol);
        assertEquals("SG SS", r.remark);
        JsonObject server = firstServer(r.outbound, "servers");
        assertEquals("aes-256-gcm", server.get("method").getAsString());
        assertEquals("secretpass", server.get("password").getAsString());
        assertEquals(8388, server.get("port").getAsInt());
    }

    @Test
    public void shadowsocksLegacyFullBase64() throws Exception {
        String body = b64("chacha20-ietf-poly1305:pw@192.0.2.9:443");
        ShareLinkParser.Result r = ShareLinkParser.parse("ss://" + body + "#legacy");

        JsonObject server = firstServer(r.outbound, "servers");
        assertEquals("chacha20-ietf-poly1305", server.get("method").getAsString());
        assertEquals("192.0.2.9", server.get("address").getAsString());
        assertEquals("legacy", r.remark);
    }

    @Test
    public void trojanTls() throws Exception {
        ShareLinkParser.Result r = ShareLinkParser.parse(
                "trojan://mypassword@198.51.100.20:443?security=tls&sni=t.example.com#Trojan%20US");

        assertEquals("trojan", r.protocol);
        assertEquals("mypassword", firstServer(r.outbound, "servers").get("password").getAsString());
        assertEquals("t.example.com", r.outbound.getAsJsonObject("streamSettings")
                .getAsJsonObject("tlsSettings").get("serverName").getAsString());
    }

    @Test
    public void hysteria2Scheme() throws Exception {
        ShareLinkParser.Result r = ShareLinkParser.parse(
                "hysteria2://authpass@203.0.113.50:8443?sni=h.example.com&insecure=1#HY2");

        assertEquals("hysteria2", r.protocol);
        assertEquals("authpass", firstServer(r.outbound, "servers").get("password").getAsString());
        assertTrue(r.outbound.getAsJsonObject("streamSettings")
                .getAsJsonObject("tlsSettings").get("allowInsecure").getAsBoolean());
    }

    @Test
    public void unknownSchemeIsRejected() {
        try {
            ShareLinkParser.parse("http://example.com");
            fail("应当拒绝未知协议");
        } catch (ShareLinkParser.ParseException expected) {
            // 预期
        }
    }

    @Test
    public void missingUuidIsRejected() {
        try {
            ShareLinkParser.parse("vless://203.0.113.10:443?security=reality&pbk=P&sni=a.com");
            fail("缺少 UUID 应当报错");
        } catch (ShareLinkParser.ParseException expected) {
            // 预期
        } catch (Exception e) {
            fail("应为 ParseException，实际 " + e);
        }
    }
}
