package com.openapi.myapp.data;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * 读取 VPN 隧道（TUN 网卡）的字节计数。
 * <p>
 * 三种协议（IKEv2、WireGuard、Xray）都通过 {@code VpnService} 建立 TUN 网卡，
 * 所以统一从 {@code /sys/class/net/tunN/statistics} 读取，不依赖各自的引擎接口。
 * 这些计数只统计经过隧道的流量，不包含直连的应用。
 */
final class TunnelTraffic {

    private TunnelTraffic() {
    }

    /** 当前所有处于 UP 状态的 tun 网卡的累计字节数；没有隧道或读取失败时返回 null。 */
    @Nullable
    static long[] read() {
        long rx = 0;
        long tx = 0;
        boolean found = false;
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.getName().startsWith("tun") || !nif.isUp()) {
                    continue;
                }
                found = true;
                rx += readCounter(nif.getName(), "rx_bytes");
                tx += readCounter(nif.getName(), "tx_bytes");
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
        return found ? new long[]{rx, tx} : null;
    }

    private static long readCounter(String iface, String name) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new FileReader("/sys/class/net/" + iface + "/statistics/" + name))) {
            String line = reader.readLine();
            return line == null ? 0 : Long.parseLong(line.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
