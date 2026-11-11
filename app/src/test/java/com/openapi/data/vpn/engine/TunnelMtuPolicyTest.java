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
        // 流式协议与链路无关，任何链路都固定 1500
        assertEquals(1500, c(TunnelMtuPolicy.Proto.STREAM, 0, 0, 0));
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
        // VLESS/Trojan/VMess 的隧道 MTU 只影响手机内部切包，与线上包大小无关：
        // 一律用 1500，不随链路变化（文档 2.2）。
        assertEquals(1500, c(TunnelMtuPolicy.Proto.STREAM, 0, 1500, 0));
        assertEquals(1500, c(TunnelMtuPolicy.Proto.STREAM, 0, 1300, 0));
        assertEquals(1500, c(TunnelMtuPolicy.Proto.STREAM, 0, 0, 0));
    }

    @Test
    public void serverValueOnlyLowers() {
        assertEquals(1300, c(TunnelMtuPolicy.Proto.IKEV2, 1300, 1500, 0));
        assertEquals(1390, c(TunnelMtuPolicy.Proto.IKEV2, 1500, 1500, 0));
        assertEquals(1280, c(TunnelMtuPolicy.Proto.IKEV2, 1200, 1500, 0));  // 不低于下限
    }

    @Test
    public void cellularAssumptionKeepsHeadroom() {
        // 蜂窝按 1400 起步（GTP 隧道常见值）；IKEv2 留 110 开销后仍高于下限
        assertEquals(1290, c(TunnelMtuPolicy.Proto.IKEV2, 0, 1400, 0));
        assertEquals(1320, c(TunnelMtuPolicy.Proto.WIREGUARD, 0, 1400, 0));
        assertEquals(1300, c(TunnelMtuPolicy.Proto.UDP_RELAY, 0, 1400, 0));
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
