package com.openapi.data.vpn.engine;

/**
 * 隧道连通性探针，sing-box 与 WireGuard 共用。
 * <p>
 * "隧道建好了"不等于"能上网"：sing-box 本机 TCP 栈会先接住连接，WireGuard 的接口 UP
 * 也不代表握手之后数据能来回。两者都要真的走一个来回才算连上，否则 App 显示"已连接"、
 * 系统 VPN 接管了所有流量，手机却完全上不了网。
 */
public final class TunnelProbe {

    static final long PROBE_TIMEOUT_MS = 20_000L;
    static final long PROBE_INTERVAL_MS = 1_000L;
    private static final int PROBE_CONNECT_TIMEOUT_MS = 3_000;
    /** TLS 握手的读超时。上游不通时多半是读超时，所以不能太长，否则总时长里轮次太少。 */
    private static final int PROBE_IO_TIMEOUT_MS = 4_000;
    /** 裸 IP，DNS 失败和隧道不通分得清；三家不同的 anycast，避免单个目标被机房出站策略拦掉误判。 */
    static final String[][] PROBE_TARGETS = {
            {"1.1.1.1", "443"},
            {"8.8.8.8", "443"},
            {"9.9.9.9", "443"},
    };

    private TunnelProbe() {
    }

    /**
     * 在总时长内反复探测，任一目标通过即返回 true。{@code alive} 返回 false 时立刻放弃。
     * <p>
     * 探测 socket 必须绑在系统的 VPN 网络上：隧道刚建好时，系统还要几十到几百毫秒才把本应用的流量
     * 切到 VPN 上，这段时间里普通 socket 会直接走 Wi-Fi/移动网络，"探测通过"其实没经过隧道
     * （实测 IKEv2 隧道建好 190ms 就"通过"，而走隧道一个来回至少 360ms）。
     * 所以先等 VPN 网络出现，再用它的 SocketFactory 建 socket；VPN 网络还没出现的轮次直接算失败、继续等。
     */
    public static Result probeUntilDeadline(android.content.Context context,
                                            java.util.function.BooleanSupplier alive) {
        android.net.ConnectivityManager cm =
                context.getApplicationContext().getSystemService(android.net.ConnectivityManager.class);
        long deadline = System.currentTimeMillis() + PROBE_TIMEOUT_MS;
        boolean seenMtuSuspect = false;
        while (System.currentTimeMillis() < deadline) {
            if (!alive.getAsBoolean()) {
                return Result.UNREACHABLE;
            }
            android.net.Network vpn = findVpnNetwork(cm);
            if (vpn != null) {
                for (String[] target : PROBE_TARGETS) {
                    Result r = probeOnce(vpn, target[0], Integer.parseInt(target[1]));
                    if (r == Result.OK) {
                        return Result.OK;
                    }
                    if (r == Result.MTU_SUSPECT) {
                        seenMtuSuspect = true;
                    }
                }
            }
            try {
                Thread.sleep(PROBE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.UNREACHABLE;
            }
        }
        return seenMtuSuspect ? Result.MTU_SUSPECT : Result.UNREACHABLE;
    }

    /**
     * 探针结果。MTU_SUSPECT 的条件：TCP 已经连上，但 TLS 握手一直等不到回包（读超时）。
     * 这通常是小包能过、大包（证书链）被路径丢弃，即 MTU 黑洞；此时才值得降到 1280 重试。
     */
    public enum Result { OK, UNREACHABLE, MTU_SUSPECT }

    /**
     * 一次端到端探测：TCP 连上之后必须再完成一次 TLS 握手，成功返回 true。
     * <p>
     * 只做 {@code connect()} 是不够的：TUN 用 mixed 栈时，TCP 由本机系统协议栈先接住，
     * connect() 在上游还没拨出去之前就会成功，线路根本不通也会"瞬间连上"。
     * TLS 握手要收到对端的 ServerHello 才算完成，只有数据真的走了一个来回才会通过；
     * 上游不通时本机栈会把连接关掉（RST/EOF），或者读超时，这里都按失败处理。
     * 只校验证书链，不校验主机名：目标是裸 IP，这里只关心"通没通"。
     */
    static Result probeOnce(android.net.Network network, String host, int port) {
        javax.net.ssl.SSLSocket ssl = null;
        java.net.Socket raw;
        try {
            raw = network.getSocketFactory().createSocket();
        } catch (java.io.IOException e) {
            return Result.UNREACHABLE;
        }
        boolean tcpUp = false;
        try {
            raw.connect(new java.net.InetSocketAddress(host, port), PROBE_CONNECT_TIMEOUT_MS);
            tcpUp = true;
            raw.setSoTimeout(PROBE_IO_TIMEOUT_MS);
            ssl = (javax.net.ssl.SSLSocket) ((javax.net.ssl.SSLSocketFactory)
                    javax.net.ssl.SSLSocketFactory.getDefault()).createSocket(raw, host, port, true);
            ssl.startHandshake();
            return Result.OK;
        } catch (java.net.SocketTimeoutException e) {
            // TCP 连上后握手读超时 → 小包可能能过、大包被丢（MTU 黑洞的典型表现）
            return tcpUp ? Result.MTU_SUSPECT : Result.UNREACHABLE;
        } catch (Exception e) {
            return Result.UNREACHABLE;
        } finally {
            try {
                if (ssl != null) {
                    ssl.close();
                } else {
                    raw.close();
                }
            } catch (Exception ignored) {
            }
        }
    }


    /** 本应用当前的 VPN 网络；系统还没把它建好时返回 null。 */
    @androidx.annotation.Nullable
    private static android.net.Network findVpnNetwork(@androidx.annotation.Nullable android.net.ConnectivityManager cm) {
        if (cm == null) {
            return null;
        }
        for (android.net.Network n : cm.getAllNetworks()) {
            android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            if (caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                return n;
            }
        }
        return null;
    }
}
