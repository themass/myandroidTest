package com.openapi.data.vpn.engine;

/**
 * 隧道连通性探针，sing-box 与 WireGuard 共用。
 * <p>
 * "隧道建好了"不等于"能上网"：sing-box 本机 TCP 栈会先接住连接，WireGuard 的接口 UP
 * 也不代表握手之后数据能来回。两者都要真的走一个来回才算连上，否则 App 显示"已连接"、
 * 系统 VPN 接管了所有流量，手机却完全上不了网。
 */
public final class TunnelProbe {

    public static final long PROBE_TIMEOUT_MS = 20_000L;
    /**
     * 用了乐观 MTU（> FLOOR）时的探测总时长。既然是猜的，就该快速验证：
     * 猜对了省下用户 12 秒等待，猜错了也能在 8 秒内降级重连。
     */
    public static final long PROBE_TIMEOUT_OPTIMISTIC_MS = 8_000L;
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
        return probeUntilDeadline(context, alive, PROBE_TIMEOUT_MS);
    }

    /**
     * @param timeoutMs 连通性判定的总时长。MTU 走乐观起点时调用方应传更短的值（8 秒），
     *                  让"猜大了"的代价可控，而不是让用户等满 20 秒再看到一次失败。
     */
    public static Result probeUntilDeadline(android.content.Context context,
                                            java.util.function.BooleanSupplier alive,
                                            long timeoutMs) {
        android.net.ConnectivityManager cm =
                context.getApplicationContext().getSystemService(android.net.ConnectivityManager.class);
        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean connected = false;
        while (System.currentTimeMillis() < deadline) {
            if (!alive.getAsBoolean()) {
                return Result.UNREACHABLE;
            }
            android.net.Network vpn = findVpnNetwork(cm);
            if (vpn != null) {
                for (String[] target : PROBE_TARGETS) {
                    if (probeOnce(vpn, target[0], Integer.parseInt(target[1])) == Result.OK) {
                        connected = true;
                        break;
                    }
                }
            }
            if (connected) {
                break;
            }
            try {
                Thread.sleep(PROBE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.UNREACHABLE;
            }
        }
        if (!connected) {
            return Result.UNREACHABLE;
        }
        // 连通性已通过：再确认"隧道 MTU 尺寸的包能不能过"。
        // 只用公共 anycast 判断不可靠——跨境线路上"TCP 通、TLS 不通"很常见，
        // 和 MTU 黑洞表现一样，会把好线路误判成需要降到 1280。自有接口不受这个影响。
        return probeLargePacket(cm, alive);
    }

    /**
     * 探针结果。
     * <ul>
     *   <li>{@link #OK} 连通性通过，且大包验证也通过（或无法验证）</li>
     *   <li>{@link #UNREACHABLE} 线路不通，与 MTU 无关</li>
     *   <li>{@link #MTU_SUSPECT} 隧道能用，但自有主机的大包过不去 → 才可能是 MTU 黑洞</li>
     * </ul>
     */
    public enum Result { OK, UNREACHABLE, MTU_SUSPECT }

    /**
     * 一次端到端探测：TCP 连上之后必须再完成一次 TLS 握手，成功返回 OK。
     * <p>
     * 只做 {@code connect()} 是不够的：TUN 用 mixed 栈时，TCP 由本机系统协议栈先接住，
     * connect() 在上游还没拨出去之前就会成功，线路根本不通也会"瞬间连上"。
     * TLS 握手要收到对端的 ServerHello 才算完成，只有数据真的走了一个来回才会通过。
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
        try {
            raw.connect(new java.net.InetSocketAddress(host, port), PROBE_CONNECT_TIMEOUT_MS);
            raw.setSoTimeout(PROBE_IO_TIMEOUT_MS);
            ssl = (javax.net.ssl.SSLSocket) ((javax.net.ssl.SSLSocketFactory)
                    javax.net.ssl.SSLSocketFactory.getDefault()).createSocket(raw, host, port, true);
            ssl.startHandshake();
            return Result.OK;
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

    /**
     * 大包验证：经 VPN 网络向<b>自有 API 主机</b>做一次完整 TLS 握手。
     * <p>
     * 为什么用自有机型而不只看响应体大小：接口只回 102 字节（之前误以为 6.5 KB，那是压缩后的原串），
     * 读不满大包，判定永远失败。而 TLS 握手里服务端的 Certificate 证书链通常有 3–5 KB，
     * 对端会按 MSS 发满尺寸的 TCP 段——这本身就足以验证"隧道 MTU 大小的包能不能过"。
     * <p>
     * 为什么不用公共 anycast：跨境线路上 1.1.1.1 / 8.8.8.8 "TCP 通、TLS 不通"很常见，
     * 表现和 MTU 黑洞一模一样，会把好线路误判成需要降到 1280。自有域名不会。
     */
    private static Result probeLargePacket(@androidx.annotation.Nullable android.net.ConnectivityManager cm,
                                           java.util.function.BooleanSupplier alive) {
        String host = com.openapi.myapp.constant.Constants.BASE_IP;
        if (host == null || host.isEmpty() || host.contains(":")) {
            return Result.OK;   // 域名带端口或异常时不作 MTU 判定
        }
        android.net.Network vpn = findVpnNetwork(cm);
        if (vpn == null || !alive.getAsBoolean()) {
            return Result.OK;
        }
        javax.net.ssl.SSLSocket ssl = null;
        boolean tcpUp = false;
        try {
            java.net.Socket raw = vpn.getSocketFactory().createSocket();
            raw.connect(new java.net.InetSocketAddress(host, 443), PROBE_CONNECT_TIMEOUT_MS);
            tcpUp = true;
            raw.setSoTimeout(PROBE_IO_TIMEOUT_MS);
            ssl = (javax.net.ssl.SSLSocket) ((javax.net.ssl.SSLSocketFactory)
                    javax.net.ssl.SSLSocketFactory.getDefault()).createSocket(raw, host, 443, true);
            ssl.startHandshake();
            return Result.OK;
        } catch (java.net.SocketTimeoutException e) {
            // TCP 通但握手上不来：证书链（大包）过不去，这才是 MTU 的嫌疑
            return tcpUp ? Result.MTU_SUSPECT : Result.OK;
        } catch (Exception e) {
            return Result.OK;   // 其它失败不归因于 MTU
        } finally {
            try {
                if (ssl != null) {
                    ssl.close();
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
