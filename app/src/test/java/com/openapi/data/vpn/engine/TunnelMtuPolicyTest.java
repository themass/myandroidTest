package com.openapi.data.vpn.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** TunnelMtuPolicy 的纯计算部分：不依赖 Android 系统。 */
public class TunnelMtuPolicyTest {

    private static int c(TunnelMtuPolicy.Proto p, int server, int link, int learned) {
        return TunnelMtuPolicy.compute(p, server, link, learned);
    }

    @Test
    public void unknownLinkFallsBackTo1280() {
        assertEquals(1280, c(TunnelMtuPolicy.Proto.IKEV2, 0, 0, 0));
        assertEquals(1280, c(TunnelMtuPolicy.Proto.WIREGUARD, 0, 0, 0));
        assertEquals(1280, c(TunnelMtuPolicy.Proto.UDP_RELAY, 0, 0, 0));
        assertEquals(1280, c(TunnelMtuPolicy.Proto.STREAM, 0, 0, 0));
    }

    @Test
    public void goodLinkUsesFullCapacity() {
        // 普通 Wi-Fi 1500：不再压在 1280，按开销算到协议上限
        assertEquals(1390, c(TunnelMtuPolicy.Proto.IKEV2, 0, 1500, 0));      // 1500-110
        assertEquals(1420, c(TunnelMtuPolicy.Proto.WIREGUARD, 0, 1500, 0));  // 1500-80
        assertEquals(1400, c(TunnelMtuPolicy.Proto.UDP_RELAY, 0, 1500, 0));  // 1500-100
        // PPPoE 1492
        assertEquals(1382, c(TunnelMtuPolicy.Proto.IKEV2, 0, 1492, 0));
    }

    @Test
    public void badLinkClampedToFloor() {
        assertEquals(1280, c(TunnelMtuPolicy.Proto.IKEV2, 0, 1340, 0));      // 1230 → 下限
    }

    @Test
    public void streamProtocolIgnoresLink() {
        // VLESS/Trojan/VMess 隧道 MTU 与线上包无关，保持 1280，不看链路
        assertEquals(1280, c(TunnelMtuPolicy.Proto.STREAM, 0, 1500, 0));
        assertEquals(1280, c(TunnelMtuPolicy.Proto.STREAM, 0, 1300, 0));
    }

    @Test
    public void serverValueOnlyLowers() {
        assertEquals(1300, c(TunnelMtuPolicy.Proto.IKEV2, 1300, 1500, 0));
        assertEquals(1390, c(TunnelMtuPolicy.Proto.IKEV2, 1500, 1500, 0));
        assertEquals(1280, c(TunnelMtuPolicy.Proto.IKEV2, 1200, 1500, 0));  // 不低于下限
    }

    @Test
    public void learnedValueWins() {
        assertEquals(1280, c(TunnelMtuPolicy.Proto.IKEV2, 0, 1500, 1280));
        assertEquals(1280, c(TunnelMtuPolicy.Proto.WIREGUARD, 0, 1500, 900));
    }

    @Test
    public void forSingBoxOutboundMapsUdpProtocols() {
        assertEquals(TunnelMtuPolicy.Proto.UDP_RELAY, TunnelMtuPolicy.forSingBoxOutbound("shadowsocks"));
        assertEquals(TunnelMtuPolicy.Proto.UDP_RELAY, TunnelMtuPolicy.forSingBoxOutbound("hysteria2"));
        assertEquals(TunnelMtuPolicy.Proto.STREAM, TunnelMtuPolicy.forSingBoxOutbound("vless"));
        assertEquals(TunnelMtuPolicy.Proto.STREAM, TunnelMtuPolicy.forSingBoxOutbound(null));
    }
}
