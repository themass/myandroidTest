package com.openapi.data.vpn.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;

import androidx.annotation.Nullable;

import java.net.InetAddress;
import java.util.Map;

/**
 * 隧道 MTU 的唯一决策来源（方案见 doc/MTU-DESIGN.md）。
 * <p>
 * 决策顺序：协议默认值 → 服务端下发 host.mtu（取较小） → 底层网络 MTU 减封装开销（仅三层封装/UDP 中继）
 * → 本网络学习值（取较小） → 夹在 [{@link #FLOOR}, 协议上限] 内。
 * <p>
 * 学习值的键 = 传输类型 + 默认网关（哈希）+ 协议 + 节点地址。只在探针判定为"疑似 MTU 黑洞"时写入 1280，
 * 7 天过期。网关哈希不含 SSID/运营商，不需要定位权限；同网关不同 Wi-Fi 会共用学习值，这是已知取舍。
 */
public final class TunnelMtuPolicy {

    /** IPv6 最小 MTU。任何链路上都安全，也是隧道 MTU 的下限。 */
    public static final int FLOOR = 1280;

    /** 协议类别。overhead 是封装开销（字节），linkDependent=false 表示隧道 MTU 与线上包大小无关。 */
    public enum Proto {
        IKEV2(110, 1400, 1280, true),
        WIREGUARD(80, 1420, 1280, true),
        UDP_RELAY(100, 1400, 1280, true),
        STREAM(0, 1500, 1280, false);

        final int overhead;
        final int cap;
        final int defaultMtu;
        final boolean linkDependent;

        Proto(int overhead, int cap, int defaultMtu, boolean linkDependent) {
            this.overhead = overhead;
            this.cap = cap;
            this.defaultMtu = defaultMtu;
            this.linkDependent = linkDependent;
        }
    }

    private static final String PREF = "tunnel_mtu_learn";
    private static final long LEARN_TTL_MS = 7L * 24 * 3600 * 1000;
    private static final int LEARN_MAX = 50;

    private TunnelMtuPolicy() {
    }

    /**
     * 纯计算：不碰系统，便于单元测试。
     *
     * @param linkMtu   底层网络 MTU，未知传 0
     * @param learned   本网络学习值，没有传 0
     */
    static int compute(Proto p, int serverMtu, int linkMtu, int learned) {
        // 乐观起点：能从链路算出来的，就用链路能支持的最大值（不压在 1280 上），
        // 实际不通时由探针判定疑似黑洞，再降到 FLOOR 并自动重连。
        int mtu;
        if (p.linkDependent && linkMtu > 0) {
            mtu = Math.min(p.cap, linkMtu - p.overhead);
        } else {
            mtu = p.defaultMtu;   // 链路未知或流式协议：保守用 1280
        }
        if (serverMtu > 0) {
            mtu = Math.min(mtu, serverMtu);
        }
        if (learned > 0) {
            mtu = Math.min(mtu, learned);
        }
        return Math.max(FLOOR, Math.min(p.cap, mtu));
    }

    /** 疑似 MTU 黑洞时，服务/引擎返回的失败原因带这个前缀，引擎据此决定是否自动降级重连。 */
    public static final String SUSPECT_MARK = "疑似 MTU";

    private static volatile boolean suspectPending;

    /** IKEv2 这类由 strongSwan 服务回报的场景：记学习值并置待重连标记。 */
    public static void recordSuspect(Proto p, @Nullable String nodeKey) {
        learn(p, nodeKey, FLOOR);
        suspectPending = true;
    }

    /** 取走待重连标记（只返回一次）。 */
    public static boolean takeSuspect() {
        boolean v = suspectPending;
        suspectPending = false;
        return v;
    }

    /** sing-box 的出站类型 → 协议类别。SS 和 Hysteria2 的 UDP 包各自封装，其余都是流式。 */
    public static Proto forSingBoxOutbound(@Nullable String outboundType) {
        if ("shadowsocks".equals(outboundType) || "hysteria2".equals(outboundType)) {
            return Proto.UDP_RELAY;
        }
        return Proto.STREAM;
    }

    /** 实际使用的入口：读系统网络和本地学习值。 */
    public static int resolve(Proto p, int serverMtu, @Nullable String nodeKey) {
        Context c = app();
        if (c == null) {
            return compute(p, serverMtu, 0, 0);
        }
        return compute(p, serverMtu, linkMtu(c), learned(c, p, nodeKey));
    }

    /** 探针判定疑似 MTU 黑洞时调用：记下本网络对这个节点的可用值，下次连接直接用。 */
    public static void learn(Proto p, @Nullable String nodeKey, int mtu) {
        Context c = app();
        if (c == null || nodeKey == null) {
            return;
        }
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String key = learnKey(c, p, nodeKey);
        if (key == null) {
            return;
        }
        SharedPreferences.Editor ed = sp.edit().putString(key, mtu + "," + System.currentTimeMillis());
        // 超过上限时删掉最旧的
        Map<String, ?> all = sp.getAll();
        if (all.size() >= LEARN_MAX) {
            java.util.List<Map.Entry<String, ?>> byTime = new java.util.ArrayList<>(all.entrySet());
            byTime.sort(java.util.Comparator.comparingLong(
                    e -> parseTs(String.valueOf(e.getValue()))));
            int toRemove = all.size() - LEARN_MAX + 1;
            for (int i = 0; i < toRemove && i < byTime.size(); i++) {
                ed.remove(byTime.get(i).getKey());
            }
        }
        ed.apply();
    }

    // ------------------------------------------------------------ internals

    private static int learned(Context c, Proto p, @Nullable String nodeKey) {
        if (nodeKey == null) {
            return 0;
        }
        String key = learnKey(c, p, nodeKey);
        if (key == null) {
            return 0;
        }
        String v = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(key, null);
        if (v == null) {
            return 0;
        }
        if (System.currentTimeMillis() - parseTs(v) > LEARN_TTL_MS) {
            return 0;
        }
        try {
            return Integer.parseInt(v.substring(0, v.indexOf(',')));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static long parseTs(String v) {
        int i = v.indexOf(',');
        try {
            return Long.parseLong(v.substring(i + 1));
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    @Nullable
    private static String learnKey(Context c, Proto p, String nodeKey) {
        String net = networkKey(c);
        return net == null ? null : net + "|" + p.name() + "|" + nodeKey;
    }

    /** 底层（非 VPN）网络的 MTU；取不到返回 0。 */
    private static int linkMtu(Context c) {
        ConnectivityManager cm = cm(c);
        Network n = underlying(cm);
        if (cm == null || n == null) {
            return 0;
        }
        LinkProperties lp = cm.getLinkProperties(n);
        return lp == null ? 0 : lp.getMtu();
    }

    /** 传输类型 + 默认网关哈希。取不到底层网络返回 null（此时不学习、不套用学习值）。 */
    @Nullable
    private static String networkKey(Context c) {
        ConnectivityManager cm = cm(c);
        Network n = underlying(cm);
        if (cm == null || n == null) {
            return null;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        String transport = caps == null ? "?"
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "wifi"
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "cell"
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "eth" : "other";
        String gw = "none";
        LinkProperties lp = cm.getLinkProperties(n);
        if (lp != null) {
            for (RouteInfo r : lp.getRoutes()) {
                InetAddress g = r.getGateway();
                if (r.isDefaultRoute() && g != null) {
                    gw = Integer.toHexString(g.getHostAddress().hashCode());
                    break;
                }
            }
        }
        return transport + ":" + gw;
    }

    /** 不是 VPN 的、有外网能力的网络：这才是隧道底下真正的链路。 */
    @Nullable
    private static Network underlying(@Nullable ConnectivityManager cm) {
        if (cm == null) {
            return null;
        }
        Network active = cm.getActiveNetwork();
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                continue;
            }
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                continue;
            }
            if (n.equals(active)) {
                return n;
            }
        }
        return null;
    }

    @Nullable
    private static ConnectivityManager cm(Context c) {
        return c.getSystemService(ConnectivityManager.class);
    }

    @Nullable
    private static Context app() {
        com.openapi.myapp.base.MyApplication app = com.openapi.myapp.base.MyApplication.getInstance();
        return app;
    }
}
