package com.openapi.data.vpn.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.openapi.data.remote.ShareLinkParser;

import org.junit.Test;

public class SingBoxConfigBuilderTest {

    private static JsonObject convertLink(String link) throws Exception {
        ShareLinkParser.Result result = ShareLinkParser.parse(link);
        return SingBoxConfigBuilder.convertOutbound(result.outbound);
    }

    @Test
    public void vlessRealityKeepsFlowAndReality() throws Exception {
        JsonObject out = convertLink("vless://11111111-2222-3333-4444-555555555555@example.com:443"
                + "?security=reality&sni=www.microsoft.com&pbk=PUBKEY&sid=abcd&fp=chrome"
                + "&flow=xtls-rprx-vision&type=tcp#reality");
        assertEquals("vless", out.get("type").getAsString());
        assertEquals("proxy", out.get("tag").getAsString());
        assertEquals("example.com", out.get("server").getAsString());
        assertEquals(443, out.get("server_port").getAsInt());
        assertEquals("11111111-2222-3333-4444-555555555555", out.get("uuid").getAsString());
        assertEquals("xtls-rprx-vision", out.get("flow").getAsString());
        JsonObject tls = out.getAsJsonObject("tls");
        assertTrue(tls.get("enabled").getAsBoolean());
        assertEquals("www.microsoft.com", tls.get("server_name").getAsString());
        assertEquals("chrome", tls.getAsJsonObject("utls").get("fingerprint").getAsString());
        JsonObject reality = tls.getAsJsonObject("reality");
        assertEquals("PUBKEY", reality.get("public_key").getAsString());
        assertEquals("abcd", reality.get("short_id").getAsString());
        assertFalse(out.has("transport"));
    }

    @Test
    public void vlessWithoutFlowOmitsFlow() throws Exception {
        JsonObject out = convertLink("vless://uuid-a@example.com:443"
                + "?security=reality&sni=www.apple.com&pbk=PK&sid=&fp=chrome&type=tcp#noflow");
        assertFalse("服务端没配 flow 时绝不能塞默认值", out.has("flow"));
    }

    @Test
    public void wsTransportMapsPathAndHost() throws Exception {
        JsonObject out = convertLink("vless://uuid-b@example.com:443"
                + "?security=tls&sni=example.com&type=ws&path=%2Fws&host=cdn.example.com#ws");
        JsonObject transport = out.getAsJsonObject("transport");
        assertEquals("ws", transport.get("type").getAsString());
        assertEquals("/ws", transport.get("path").getAsString());
        assertEquals("cdn.example.com",
                transport.getAsJsonObject("headers").get("Host").getAsString());
    }

    @Test
    public void trojanUsesTls() throws Exception {
        JsonObject out = convertLink("trojan://secret@t.example.com:8443?sni=t.example.com#trojan");
        assertEquals("trojan", out.get("type").getAsString());
        assertEquals("secret", out.get("password").getAsString());
        assertEquals(8443, out.get("server_port").getAsInt());
        assertEquals("t.example.com", out.getAsJsonObject("tls").get("server_name").getAsString());
    }

    @Test
    public void shadowsocksMapsMethod() throws Exception {
        JsonObject out = convertLink("ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpwYXNz@1.2.3.4:8388#ss");
        assertEquals("shadowsocks", out.get("type").getAsString());
        assertEquals("chacha20-ietf-poly1305", out.get("method").getAsString());
        assertEquals("pass", out.get("password").getAsString());
        assertFalse(out.has("tls"));
    }

    @Test
    public void hysteria2UsesPasswordAndTls() throws Exception {
        JsonObject out = convertLink("hy2://pw@h.example.com:443?sni=h.example.com#hy2");
        assertEquals("hysteria2", out.get("type").getAsString());
        assertEquals("pw", out.get("password").getAsString());
        assertTrue(out.getAsJsonObject("tls").get("enabled").getAsBoolean());
    }

    @Test
    public void unsupportedTransportIsRejectedNotDowngraded() {
        JsonObject xray = JsonParser.parseString("{\"protocol\":\"vless\","
                + "\"settings\":{\"vnext\":[{\"address\":\"a.com\",\"port\":443,"
                + "\"users\":[{\"id\":\"u\"}]}]},"
                + "\"streamSettings\":{\"network\":\"splithttp\",\"security\":\"tls\"}}")
                .getAsJsonObject();
        try {
            SingBoxConfigBuilder.convertOutbound(xray);
            fail("xhttp 没有 sing-box 实现，必须明确拒绝");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("splithttp"));
        }
    }

    @Test
    public void buildConfigRoutesEverythingThroughProxy() throws Exception {
        JsonObject proxy = convertLink("trojan://secret@t.example.com:443#t");
        String json = SingBoxConfigBuilder.buildConfig(proxy, "172.19.0.1/30", 1400, "1.1.1.1,8.8.8.8");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("proxy", root.getAsJsonObject("route").get("final").getAsString());
        JsonObject tun = root.getAsJsonArray("inbounds").get(0).getAsJsonObject();
        assertEquals("tun", tun.get("type").getAsString());
        assertEquals("172.19.0.1/30", tun.getAsJsonArray("address").get(0).getAsString());
        assertEquals(1400, tun.get("mtu").getAsInt());
        assertEquals("1.1.1.1", root.getAsJsonObject("dns").getAsJsonArray("servers")
                .get(0).getAsJsonObject().get("server").getAsString());
    }
}
