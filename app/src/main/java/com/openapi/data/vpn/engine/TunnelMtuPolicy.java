package com.openapi.data.vpn.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;

import androidx.annotation.NonNull;
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
        STREAM(0, 1500, 1500, false);

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
    /** 学习值有效期。链路 MTU 会随换路由器/换运营商/漫游变化，7 天太长。 */
    private static final long LEARN_TTL_MS = 3L * 24 * 3600 * 1000;
    private static final int LEARN_MAX = 50;
    /** 同一网络同一节点连续这么多次用学习值连上都成功，尝试向上提一档。 */
    private static final int PROMOTE_AFTER_OK = 3;
    /** 每次向上提的步长；探针再失败就重新锁回 FLOOR。 */
    private static final int PROMOTE_STEP = 100;

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

    /**
     * 本次降级重连临时压到 FLOOR 的节点（键 = 协议|节点）。**只是内存标记，不落盘**：
     * 探针确认降级成功才写学习值，失败就丢掉，避免"误判一次、锁死三天"。
     */
    private static final java.util.Set<String> PENDING_FALLBACK =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private static String pendingKey(Proto p, @Nullable String nodeKey) {
        return nodeKey == null ? null : p.name() + "|" + nodeKey;
    }

    /** 判定疑似 MTU 黑洞、准备用 FLOOR 重连时调用（不落盘）。 */
    public static void beginFallback(Proto p, @Nullable String nodeKey) {
        String k = pendingKey(p, nodeKey);
        if (k != null) {
            PENDING_FALLBACK.add(k);
        }
    }

    /** 降级重连**成功**：把 FLOOR 写进学习表，并清掉临时标记。 */
    public static void confirmFallback(Proto p, @Nullable String nodeKey) {
        String k = pendingKey(p, nodeKey);
        if (k != null) {
            PENDING_FALLBACK.remove(k);
        }
        learn(p, nodeKey, FLOOR);
    }

    /** 降级重连也失败：丢弃临时标记，不写学习值。 */
    public static void cancelFallback(Proto p, @Nullable String nodeKey) {
        String k = pendingKey(p, nodeKey);
        if (k != null) {
            PENDING_FALLBACK.remove(k);
        }
    }

    private static boolean isFallbackPending(Proto p, String nodeKey) {
        String k = pendingKey(p, nodeKey);
        return k != null && PENDING_FALLBACK.contains(k);
    }

    /** IKEv2 这类由 strongSwan 服务回报的场景：只置待重连标记，学习值等重连成功再写。 */
    public static void recordSuspect(Proto p, @Nullable String nodeKey) {
        beginFallback(p, nodeKey);
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

    /**
     * 最近一次实际用掉的 MTU 快照（协议 / 节点 / 服务端值 / 结果）。切网重算时用它做对比，
     * 避免依赖隧道接口去反查（各协议读法不同）。
     */
    public static final class Snapshot {
        public final Proto proto;
        public final String nodeKey;
        public final int serverMtu;
        public final int mtu;

        Snapshot(Proto proto, String nodeKey, int serverMtu, int mtu) {
            this.proto = proto;
            this.nodeKey = nodeKey;
            this.serverMtu = serverMtu;
            this.mtu = mtu;
        }
    }

    @Nullable
    private static volatile Snapshot lastResolved;

    /** 最近一次用掉的 MTU；还没连过时为 null。 */
    @Nullable
    public static Snapshot lastResolved() {
        return lastResolved;
    }

    /** 切网后重算：同一协议、同一节点，按当前链路重新算一次。 */
    public static int recompute(@NonNull Snapshot snap) {
        return resolve(snap.proto, snap.serverMtu, snap.nodeKey);
    }

    /** 实际使用的入口：读系统网络和本地学习值。 */
    public static int resolve(Proto p, int serverMtu, @Nullable String nodeKey) {
        Context c = app();
        if (c == null) {
            return compute(p, serverMtu, 0, 0);
        }
        int mtu = compute(p, serverMtu, linkMtu(c), learned(c, p, nodeKey));
        if (isFallbackPending(p, nodeKey)) {
            mtu = Math.min(mtu, FLOOR);
        }
        lastResolved = new Snapshot(p, nodeKey, serverMtu, mtu);
        return mtu;
    }

    /**
     * 探针判定疑似 MTU 黑洞、**并且降级重连已经成功** 之后才调用。
     * 之前是降级前就写，重试也失败时错误的学习值照样留 7 天，会把用户锁死在 1280。
     */
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
        SharedPreferences.Editor ed = sp.edit()
                .putString(key, mtu + "," + System.currentTimeMillis() + ",0");
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
            int mtu = Integer.parseInt(v.substring(0, v.indexOf(',')));
            // 降级成功后已经连续成功过若干次：尝试往上提一档，验证链路其实没那么差
            if (okStreak(v) >= PROMOTE_AFTER_OK && mtu < p.cap) {
                return Math.min(p.cap, mtu + PROMOTE_STEP);
            }
            return mtu;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * 用学习值连接成功时调用。连续成功 {@link #PROMOTE_AFTER_OK} 次后，下一次会尝试提一档；
     * 提上去又失败会重新 {@link #learn} 回 FLOOR，形成闭环。这是"一次误判锁死好几天"的保险。
     */
    public static void recordSuccess(Proto p, @Nullable String nodeKey) {
        Context c = app();
        if (c == null || nodeKey == null) {
            return;
        }
        String key = learnKey(c, p, nodeKey);
        if (key == null) {
            return;
        }
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String v = sp.getString(key, null);
        if (v == null || System.currentTimeMillis() - parseTs(v) > LEARN_TTL_MS) {
            return;
        }
        try {
            int mtu = Integer.parseInt(v.substring(0, v.indexOf(',')));
            int streak = okStreak(v) + 1;
            sp.edit().putString(key, mtu + "," + System.currentTimeMillis() + "," + streak).apply();
        } catch (RuntimeException ignored) {
        }
    }

    /** 第三条字段是连续成功次数；老格式只有两段，按 0 处理。 */
    private static int okStreak(String v) {
        int first = v.indexOf(',');
        int second = first < 0 ? -1 : v.indexOf(',', first + 1);
        if (second < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(v.substring(second + 1));
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

    /**
     * 底层（非 VPN）网络的 MTU；完全取不到返回 0（此时按保守默认值走）。
     * <p>
     * 三级取值，逐级回退。不能只信 {@link LinkProperties#getMtu()}：实测小米 Android 15 上
     * Wi-Fi 网络报的是 {@code MTU: 0}，只看它就会让所有协议恒等于 1280，"按链路推算"形同虚设。
     */
    static int linkMtu(Context c) {
        ConnectivityManager cm = cm(c);
        Network n = underlying(cm);
        if (cm == null || n == null) {
            return 0;
        }
        LinkProperties lp = cm.getLinkProperties(n);
        // 1. 系统给了真实值
        if (lp != null && lp.getMtu() > 0) {
            return lp.getMtu();
        }
        // 2. 直接问网卡（普通应用可读，不需要权限）
        int fromNic = interfaceMtu(lp == null ? null : lp.getInterfaceName());
        if (fromNic > 0) {
            return fromNic;
        }
        // 3. 按传输类型给保守假设。猜错了由探针在 8 秒内降回 1280，猜对了用户直接多一截带宽。
        //    4G/5G 的 GTP 隧道多以 1400 起步，运营商再低的部分同样靠探针兜底。
        switch (networkType(c)) {
            case "wifi":
            case "eth":
                return 1500;
            case "cell":
                return 1400;
            default:
                return 0;
        }
    }

    /** {@link NetworkInterface#getMTU()}；拿不到返回 0。 */
    private static int interfaceMtu(@Nullable String name) {
        if (name == null) {
            return 0;
        }
        try {
            java.net.NetworkInterface nif = java.net.NetworkInterface.getByName(name);
            return nif == null ? 0 : Math.max(0, nif.getMTU());
        } catch (Exception e) {
            return 0;
        }
    }

    /** 底层网络的传输类型：wifi / cell / eth / other / none（上报用，不含任何标识）。 */
    static String networkType(Context c) {
        ConnectivityManager cm = cm(c);
        Network n = underlying(cm);
        if (cm == null || n == null) {
            return "none";
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        if (caps == null) {
            return "other";
        }
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "wifi"
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "cell"
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "eth" : "other";
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
