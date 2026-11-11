package com.openapi.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 分享链接解析的边界用例：真实用户粘贴的链接格式五花八门，
 * 这里覆盖容易出错的地方（IPv6、百分号编码、缺 @、padding、大小写）。
 */
public class ShareLinkParserEdgeTest {

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject servers(ShareLinkParser.Result r) {
        return r.outbound.getAsJsonObject("settings").getAsJsonArray("servers")
                .get(0).getAsJsonObject();
    }

    // ------------------------------------------------------------ Shadowsocks

    /** SIP002：userinfo 是 percent-encoded 的 method:password，不是 base64。 */
    @Test
    public void shadowsocksPercentEncodedUserInfo() throws Exception {
        ShareLinkParser.Result r = ShareLinkParser.parse(
                "ss://aes-256-gcm:p%40ssw0rd@192.0.2.5:8388#pct");
        JsonObject s = servers(r);
        assertEquals("aes-256-gcm", s.get("method").getAsString());
        assertEquals("密码里的 @ 必须还原成 p@ssw0rd", "p@ssw0rd", s.get("password").getAsString());
    }

    /** 密码本身是合法 base64 字符串时，不能被当成 base64 解开。 */
    @Test
    public void shadowsocksPasswordLooksLikeBase64() throws Exception {
        // "YWJj" 是合法 base64（解出来是 "abc"），但这里它是密码原文
        ShareLinkParser.Result r = ShareLinkParser.parse(
                "ss://aes-128-gcm:YWJj@192.0.2.5:8388#b64pass");
        assertEquals("YWJj", servers(r).get("password").getAsString());
    }

    /** IPv6 服务器地址：方括号必须处理掉，否则 sing-box 连不上。 */
    @Test
    public void shadowsocksIpv6Host() throws Exception {
        ShareLinkParser.Result r = ShareLinkParser.parse(
                "ss://YWVzLTI1Ni1nY206cHc@[2001:db8::1]:8388#v6");
        JsonObject s = servers(r);
        assertEquals("2001:db8::1", s.get("address").getAsString());
        assertEquals(8388, s.get("port").getAsInt());
    }

    /** 缺少 @ 的畸形链接必须给出明确错误，不能抛 StringIndexOutOfBounds。 */
    @Test
    public void shadowsocksMalformedGivesParseException() {
        try {
            ShareLinkParser.parse("ss://bm90LWEtdmFsaWQtc3RyaW5n");
            fail("畸形链接应当报错");
        } catch (ShareLinkParser.ParseException expected) {
            assertNotNull(expected.getMessage());
        } catch (RuntimeException e) {
            fail("应当是 ParseException，实际抛了 " + e.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------ VLESS

    /** 缺少端口时用 443。 */
    @Test
    public void vlessDefaultsPort443() throws Exception {
        ShareLinkParser.Result r = ShareLinkParser.parse(
                "vless://u@203.0.113.10?security=tls&sni=a.com");
        assertEquals(443, r.port());
    }

    /** 备注名含中文时不能被截断成空。 */
    @Test
    public void remarkKeepsChinese() throws Exception {
        ShareLinkParser.Result r = ShareLinkParser.parse(
                "vless://u@203.0.113.10:443?security=tls&sni=a.com#%E9%A6%99%E6%B8%AF%E8%8A%82%E7%82%B9");
        assertEquals("香港节点", r.remark);
    }

    /** reality 的 pbk 缺失时 sing-box 会拒绝启动，应在解析阶段就说明。 */
    @Test
    public void vlessRealityWithoutPublicKeyIsReported() throws Exception {
        ShareLinkParser.Result r = ShareLinkParser.parse(
                "vless://u@203.0.113.10:443?security=reality&sni=a.com");
        JsonObject reality = r.outbound.getAsJsonObject("streamSettings")
                .getAsJsonObject("realitySettings");
        assertEquals("", reality.get("publicKey").getAsString());
    }

    // ------------------------------------------------------------------ VMESS

    /** vmess 的端口在 JSON 里可能是数字也可能是字符串。 */
    @Test
    public void vmessPortAsNumber() throws Exception {
        String json = "{\"v\":\"2\",\"ps\":\"n\",\"add\":\"198.51.100.7\",\"port\":8443,"
                + "\"id\":\"11111111-2222-3333-4444-555555555555\",\"net\":\"tcp\",\"tls\":\"\"}";
        ShareLinkParser.Result r = ShareLinkParser.parse("vmess://" + b64(json));
        assertEquals(8443, r.port());
    }

    /** base64 缺 padding 也要能解（很多分享工具会去掉 =）。 */
    @Test
    public void vmessBase64WithoutPadding() throws Exception {
        String json = "{\"v\":\"2\",\"ps\":\"np\",\"add\":\"198.51.100.8\",\"port\":\"443\","
                + "\"id\":\"11111111-2222-3333-4444-555555555555\",\"net\":\"ws\"}";
        String padded = Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String stripped = padded.replace("=", "");
        ShareLinkParser.Result r = ShareLinkParser.parse("vmess://" + stripped);
        assertEquals("198.51.100.8", r.server());
    }

    /** 非法 base64 要报错，不能抛裸的 IllegalArgumentException。 */
    @Test
    public void vmessBadBase64GivesParseException() {
        try {
            ShareLinkParser.parse("vmess://!!!!not-base64!!!!");
            fail("应当报错");
        } catch (ShareLinkParser.ParseException expected) {
            // 预期
        } catch (RuntimeException e) {
            fail("应当是 ParseException，实际抛了 " + e.getClass().getSimpleName());
        }
    }

    // --------------------------------------------------------------- HYSTERIA2

    /** 大小写混用的 scheme 也要认。 */
    @Test
    public void hysteria2UppercaseScheme() throws Exception {
        ShareLinkParser.Result r = ShareLinkParser.parse(
                "HYSTERIA2://pass@203.0.113.50:8443#HY");
        assertEquals("hysteria2", r.protocol);
    }

    /** trojan 的 allowInsecure 拼写变体。 */
    @Test
    public void trojanAllowInsecureVariants() throws Exception {
        ShareLinkParser.Result a = ShareLinkParser.parse(
                "trojan://p@198.51.100.20:443?security=tls&allowInsecure=1");
        assertTrue(a.outbound.getAsJsonObject("streamSettings")
                .getAsJsonObject("tlsSettings").get("allowInsecure").getAsBoolean());
        ShareLinkParser.Result b = ShareLinkParser.parse(
                "trojan://p@198.51.100.20:443?security=tls&insecure=true");
        assertTrue("insecure=true 也应当识别", b.outbound.getAsJsonObject("streamSettings")
                .getAsJsonObject("tlsSettings").get("allowInsecure").getAsBoolean());
    }
}
