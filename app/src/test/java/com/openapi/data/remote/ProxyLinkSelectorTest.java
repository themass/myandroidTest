package com.openapi.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class ProxyLinkSelectorTest {

    private static final String TROJAN = "trojan://secret@t.example.com:8443?security=tls&sni=t.example.com&insecure=1#t";
    private static final String HY2 = "hysteria2://pw@h.example.com:8444?sni=h.example.com&insecure=1#h";
    private static final String SS = "ss://"
            + Base64.getEncoder().withoutPadding().encodeToString(
            "chacha20-ietf-poly1305:pass".getBytes(StandardCharsets.UTF_8))
            + "@s.example.com:8388#s";
    private static final String VMESS = "vmess://" + Base64.getEncoder().encodeToString(
            ("{\"v\":\"2\",\"ps\":\"v\",\"add\":\"v.example.com\",\"port\":\"8880\","
                    + "\"id\":\"11111111-2222-3333-4444-555555555555\",\"aid\":\"0\",\"net\":\"ws\","
                    + "\"path\":\"/ws\",\"tls\":\"\"}").getBytes(StandardCharsets.UTF_8));

    private static String json(String... pairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(pairs[i]).append("\":\"").append(pairs[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }

    @Test
    public void ordersTcpProtocolsBeforeHysteria2() {
        String links = json("hysteria2", HY2, "shadowsocks", SS, "trojan", TROJAN, "vmess", VMESS);
        List<ProxyLinkSelector.Candidate> list =
                ProxyLinkSelector.candidates("hysteria2,shadowsocks,trojan,vmess", links);
        assertEquals(4, list.size());
        assertEquals("trojan", list.get(0).protocol);
        assertEquals("vmess", list.get(1).protocol);
        assertEquals("shadowsocks", list.get(2).protocol);
        assertEquals("hysteria2", list.get(3).protocol);
    }

    @Test
    public void candidateCarriesServerAndPortFromTheLink() {
        List<ProxyLinkSelector.Candidate> list = ProxyLinkSelector.candidates("trojan", json("trojan", TROJAN));
        assertEquals(1, list.size());
        assertEquals("t.example.com", list.get(0).result.server());
        assertEquals(8443, list.get(0).result.port());
    }

    @Test
    public void skipsProtocolsTheNodeDoesNotDeclare() {
        String links = json("trojan", TROJAN, "hysteria2", HY2);
        List<ProxyLinkSelector.Candidate> list = ProxyLinkSelector.candidates("ikev2,hysteria2", links);
        assertEquals(1, list.size());
        assertEquals("hysteria2", list.get(0).protocol);
    }

    @Test
    public void protocolNamesAreCaseAndWhitespaceInsensitive() {
        List<ProxyLinkSelector.Candidate> list =
                ProxyLinkSelector.candidates(" Trojan , IKEV2 ", json("TROJAN", TROJAN));
        assertEquals(1, list.size());
    }

    @Test
    public void rejectsLinkWhoseSchemeDoesNotMatchTheKey() {
        // 后台把 vmess 链接填进了 trojan 的键里
        List<ProxyLinkSelector.Candidate> list = ProxyLinkSelector.candidates("trojan", json("trojan", VMESS));
        assertTrue(list.isEmpty());
    }

    @Test
    public void oneBrokenLinkDoesNotHideTheOthers() {
        String links = json("trojan", "trojan://", "hysteria2", HY2);
        List<ProxyLinkSelector.Candidate> list = ProxyLinkSelector.candidates("trojan,hysteria2", links);
        assertEquals(1, list.size());
        assertEquals("hysteria2", list.get(0).protocol);
    }

    @Test
    public void emptyOrMalformedInputGivesNoCandidates() {
        assertTrue(ProxyLinkSelector.candidates(null, json("trojan", TROJAN)).isEmpty());
        assertTrue(ProxyLinkSelector.candidates("trojan", null).isEmpty());
        assertTrue(ProxyLinkSelector.candidates("trojan", "").isEmpty());
        assertTrue(ProxyLinkSelector.candidates("trojan", "not json").isEmpty());
        assertTrue(ProxyLinkSelector.candidates("trojan", "[\"trojan\"]").isEmpty());
        assertTrue(ProxyLinkSelector.candidates("trojan", "{\"trojan\":123}").isEmpty());
    }

    @Test
    public void ikev2OnlyNodeHasNoProxyCandidates() {
        assertTrue(ProxyLinkSelector.candidates("ikev2", json("trojan", TROJAN)).isEmpty());
    }
}
