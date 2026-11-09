package com.openapi.data.vpn.engine;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.openapi.common.util.LogUtil;
import com.openapi.domain.model.SplitTunnelingPolicy;
import com.openapi.ks.free1.R;
import com.openapi.ks.ui.main.MainFragmentViewPage;

import io.nekohasekai.libbox.CommandServer;
import io.nekohasekai.libbox.CommandServerHandler;
import io.nekohasekai.libbox.ConnectionOwner;
import io.nekohasekai.libbox.InterfaceUpdateListener;
import io.nekohasekai.libbox.Libbox;
import io.nekohasekai.libbox.LocalDNSTransport;
import io.nekohasekai.libbox.NetworkInterfaceIterator;
import io.nekohasekai.libbox.OverrideOptions;
import io.nekohasekai.libbox.PlatformInterface;
import io.nekohasekai.libbox.RoutePrefix;
import io.nekohasekai.libbox.RoutePrefixIterator;
import io.nekohasekai.libbox.SetupOptions;
import io.nekohasekai.libbox.StringBox;
import io.nekohasekai.libbox.StringIterator;
import io.nekohasekai.libbox.SystemProxyStatus;
import io.nekohasekai.libbox.TunOptions;
import io.nekohasekai.libbox.WIFIState;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;

/**
 * sing-box（libbox）承载的 {@link VpnService}，同时承载 VLESS+Reality 和用户自己的节点。
 * <p>
 * libbox 自己跑 tun 栈、路由和 DNS；这里只实现它要求的 {@link PlatformInterface}：
 * 建立 TUN（地址、路由、DNS 都由 sing-box 的配置决定）、给出口 socket 做 protect、
 * 报告默认网络接口。分应用代理由 {@link VpnService.Builder} 完成，和其它引擎一致。
 * <p>
 * 与 {@code CharonVpnService}（IKEv2）、{@code GoBackend$VpnService}（WireGuard）
 * 共存没有问题；真正的互斥由 {@code CharonVpnSessionRepository.connect()} 保证。
 */
public final class SingBoxVpnService extends VpnService {

    private static final String TAG = "SingBoxVpnService";
    /** 与 {@code CharonVpnService.FOREGROUND_NOTIFY_ID}（100）错开，避免切换协议时通知互相覆盖。 */
    private static final int FOREGROUND_NOTIFY_ID = 200;
    private static final String NOTIFY_CHANNEL = "SINGBOX_VPN_CHANNEL";
    private static final String DEFAULT_SESSION = "sing-box";
    private static final String DEFAULT_TUN_ROUTE_V4 = "0.0.0.0";
    private static final String DEFAULT_TUN_ROUTE_V6 = "::";

    /**
     * 连通性探针：总时长 / 单次超时 / 间隔 / 目标。
     * 目标用裸 IP，DNS 失败和隧道不通分得清；选三个不同大厂的 anycast 地址，
     * 避免单个目标被机房出站策略拦掉误判。
     */
    private static final long PROBE_TIMEOUT_MS = 20_000L;
    private static final int PROBE_CONNECT_TIMEOUT_MS = 3_000;
    /** TLS 握手的读超时。上游不通时多半是读超时，所以不能太长，否则 20 秒总时长里轮次太少。 */
    private static final int PROBE_IO_TIMEOUT_MS = 4_000;
    private static final long PROBE_INTERVAL_MS = 1_000L;
    private static final String[][] PROBE_TARGETS = {
            {"1.1.1.1", "443"},
            {"8.8.8.8", "443"},
            {"9.9.9.9", "443"},
    };

    public static final String ACTION_CONNECT = "com.openapi.singbox.CONNECT";
    public static final String ACTION_DISCONNECT = "com.openapi.singbox.DISCONNECT";
    public static final String EXTRA_CONFIG_JSON = "singbox_config_json";
    public static final String EXTRA_SPLIT_MODE = "split_mode";
    public static final String EXTRA_SPLIT_PACKAGES = "split_packages";

    /** 进程内唯一监听者；这个服务同一时刻只承载一条隧道。 */
    public interface Listener {
        void onEstablished();

        void onFailed(String reason);

        void onStopped();
    }

    @Nullable
    private static volatile Listener listener;
    private static volatile boolean libboxReady;

    public static void setListener(@Nullable Listener l) {
        listener = l;
    }

    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final PlatformImpl platform = new PlatformImpl();
    private final CommandHandler commandHandler = new CommandHandler();

    @Nullable
    private CommandServer commandServer;
    /** 从 connect 到 teardown 之间为 true，探针和回调据此判断隧道是否还活着。 */
    private volatile boolean running;
    @Nullable
    private String splitMode;
    @Nullable
    private String[] splitPackages;
    @Nullable
    private ConnectivityManager.NetworkCallback defaultNetworkCallback;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_DISCONNECT.equals(action)) {
            teardown();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_CONNECT.equals(action)) {
            doConnect(intent);
        }
        return START_NOT_STICKY;
    }

    private void doConnect(Intent intent) {
        // 必须最先调用：没有前台通知，华为 EMUI/HarmonyOS 这类对后台管控激进的系统会在
        // TUN 建立后几百毫秒内杀掉 Service，表现为"应用显示已连接，状态栏没有 VPN 钥匙"。
        startForeground(FOREGROUND_NOTIFY_ID, buildNotification());

        String configJson = intent.getStringExtra(EXTRA_CONFIG_JSON);
        splitMode = intent.getStringExtra(EXTRA_SPLIT_MODE);
        splitPackages = intent.getStringArrayExtra(EXTRA_SPLIT_PACKAGES);
        if (configJson == null) {
            LogUtil.e(TAG + ": 启动参数缺失 config");
            notifyFailed("missing config");
            stopForeground(true);
            stopSelf();
            return;
        }

        running = true;
        // startOrReloadService 会同步回调 openTun 并等待内核就绪，不能放在主线程上，否则 ANR。
        new Thread(() -> startService(configJson), "singbox-start").start();
    }

    private void startService(String configJson) {
        try {
            ensureLibboxSetup();
            commandServer = Libbox.newCommandServer(commandHandler, platform);
            commandServer.start();
            commandServer.startOrReloadService(configJson, new OverrideOptions());
            LogUtil.i(TAG + ": sing-box 已启动，开始连通性探测");
            new Thread(this::probeThenNotify, "singbox-probe").start();
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            LogUtil.e(TAG + ": sing-box 启动失败: " + reason);
            mainHandler.post(() -> {
                notifyFailed(reason);
                teardown();
                stopSelf();
            });
        }
    }

    /**
     * 真实连通性探针（后台线程）：只有能通过隧道 TCP 连到公网，才算真的连上。
     * 必须在后台跑，doConnect 在主线程，阻塞会 ANR。
     */
    private void probeThenNotify() {
        long deadline = System.currentTimeMillis() + PROBE_TIMEOUT_MS;
        int round = 0;
        while (System.currentTimeMillis() < deadline) {
            if (!running) {
                LogUtil.i(TAG + ": 探针中止，隧道已拆除");
                return;
            }
            round++;
            for (String[] target : PROBE_TARGETS) {
                if (probeOnce(target[0], Integer.parseInt(target[1]))) {
                    LogUtil.i(TAG + ": 连通性探针通过（第 " + round + " 轮，"
                            + target[0] + ":" + target[1] + "）");
                    if (running) {
                        notifyEstablished();
                    }
                    return;
                }
            }
            try {
                Thread.sleep(PROBE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        String reason = "隧道已建立但探测流量不通（握手失败或节点参数不对）";
        LogUtil.e(TAG + ": " + reason);
        mainHandler.post(() -> {
            if (!running) {
                return;
            }
            notifyFailed(reason);
            teardown();
            stopSelf();
        });
    }

    /**
     * 一次端到端探测：TCP 连上之后必须再完成一次 TLS 握手，成功返回 true。
     * <p>
     * 只做 {@code connect()} 是不够的：TUN 用 mixed 栈时，TCP 由本机系统协议栈先接住，
     * connect() 在上游还没拨出去之前就会成功，线路根本不通也会"瞬间连上"。
     * TLS 握手要收到对端的 ServerHello 才算完成，只有数据真的走了一个来回才会通过；
     * 上游不通时本机栈会把连接关掉（RST/EOF），或者读超时，这里都按失败处理。
     * 只校验证书链，不校验主机名：目标是裸 IP，这里只关心"通没通"。
     */
    private static boolean probeOnce(String host, int port) {
        java.net.Socket raw = new java.net.Socket();
        javax.net.ssl.SSLSocket ssl = null;
        try {
            raw.connect(new java.net.InetSocketAddress(host, port), PROBE_CONNECT_TIMEOUT_MS);
            raw.setSoTimeout(PROBE_IO_TIMEOUT_MS);
            ssl = (javax.net.ssl.SSLSocket) ((javax.net.ssl.SSLSocketFactory)
                    javax.net.ssl.SSLSocketFactory.getDefault()).createSocket(raw, host, port, true);
            ssl.startHandshake();
            return true;
        } catch (Exception e) {
            return false;
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

    /** libbox 全进程只能 setup 一次；工作目录放在应用私有目录下。 */
    private synchronized void ensureLibboxSetup() throws Exception {
        if (libboxReady) {
            return;
        }
        File workDir = new File(getFilesDir(), "singbox");
        if (!workDir.exists() && !workDir.mkdirs()) {
            throw new IOException("无法创建 sing-box 工作目录: " + workDir);
        }
        SetupOptions options = new SetupOptions();
        options.setBasePath(getFilesDir().getAbsolutePath());
        options.setWorkingPath(workDir.getAbsolutePath());
        options.setTempPath(getCacheDir().getAbsolutePath());
        options.setCommandServerListenPort(freeLocalPort());
        options.setCommandServerSecret(UUID.randomUUID().toString());
        Libbox.setup(options);
        libboxReady = true;
    }

    private static int freeLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** 分应用代理。与其它引擎一致：include/exclude 互斥，已卸载的包跳过而不是让整次连接失败。 */
    private void applySplitTunneling(VpnService.Builder builder) {
        if (splitPackages == null || splitPackages.length == 0) {
            return;
        }
        boolean include = SplitTunnelingPolicy.Mode.INCLUDE.name().equalsIgnoreCase(splitMode);
        for (String pkg : splitPackages) {
            try {
                if (include) {
                    builder.addAllowedApplication(pkg);
                } else {
                    builder.addDisallowedApplication(pkg);
                }
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                LogUtil.w(TAG + ": split tunneling 包不存在 " + pkg);
            }
        }
    }

    private void teardown() {
        running = false;
        stopDefaultInterfaceCallback();
        CommandServer server = commandServer;
        commandServer = null;
        if (server != null) {
            try {
                server.closeService();
            } catch (Exception e) {
                LogUtil.e(TAG + ": closeService 异常: " + e.getMessage());
            }
            server.close();
        }
        stopForeground(true);
        notifyStopped();
    }

    @Override
    public void onDestroy() {
        teardown();
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        teardown();
        stopSelf();
        super.onRevoke();
    }

    private void notifyEstablished() {
        Listener l = listener;
        if (l != null) {
            l.onEstablished();
        }
    }

    private void notifyFailed(String reason) {
        Listener l = listener;
        if (l != null) {
            l.onFailed(reason);
        }
    }

    private void notifyStopped() {
        Listener l = listener;
        if (l != null) {
            l.onStopped();
        }
    }

    /** 默认网络变化时通知 libbox，让它的直连流量绕开 VPN 自己的网络。 */
    private void startDefaultInterfaceCallback(InterfaceUpdateListener target) {
        stopDefaultInterfaceCallback();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null) {
            return;
        }
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        defaultNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                report(cm, network, cm.getLinkProperties(network), target);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                report(cm, network, cm.getLinkProperties(network), target);
            }

            @Override
            public void onLinkPropertiesChanged(Network network, LinkProperties lp) {
                report(cm, network, lp, target);
            }

            @Override
            public void onLost(Network network) {
                target.updateDefaultInterface("", -1, false, false);
            }
        };
        cm.registerNetworkCallback(request, defaultNetworkCallback);
    }

    private static void report(ConnectivityManager cm, Network network, @Nullable LinkProperties lp,
                               InterfaceUpdateListener target) {
        if (lp == null || lp.getInterfaceName() == null) {
            return;
        }
        String name = lp.getInterfaceName();
        int index;
        try {
            java.net.NetworkInterface nif = java.net.NetworkInterface.getByName(name);
            index = nif == null ? -1 : nif.getIndex();
        } catch (IOException e) {
            index = -1;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        boolean expensive = caps == null
                || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        target.updateDefaultInterface(name, index, expensive, false);
    }

    private void stopDefaultInterfaceCallback() {
        ConnectivityManager.NetworkCallback callback = defaultNetworkCallback;
        defaultNetworkCallback = null;
        if (callback == null) {
            return;
        }
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) {
            try {
                cm.unregisterNetworkCallback(callback);
            } catch (IllegalArgumentException ignored) {
                // 已经注销过（网络回调只能注销一次），忽略。
            }
        }
    }

    /**
     * 构造前台服务通知。只做最小实现：目的是让系统承认这是前台服务，
     * 不需要复刻 {@code CharonVpnService} 那套富交互通知。
     */
    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFY_CHANNEL, DEFAULT_SESSION, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(DEFAULT_SESSION + " 隧道运行状态");
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }

        Intent contentIntent = new Intent(this, MainFragmentViewPage.class);
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, contentIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, NOTIFY_CHANNEL)
                .setContentTitle(getString(R.string.vpn_remote_on))
                .setContentText(DEFAULT_SESSION)
                .setSmallIcon(R.drawable.vpn_on)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSound(null)
                .setVibrate(new long[]{0})
                .build();
    }

    /** libbox 回调我们的平台能力。这里只实现 sing-box 在 Android 上真正用到的部分。 */
    private final class PlatformImpl implements PlatformInterface {

        /** libbox 每建一个出口 socket 都会调用：protect 掉，否则会被 0.0.0.0/0 路由回环进 TUN。 */
        @Override
        public void autoDetectInterfaceControl(int fd) throws Exception {
            if (!protect(fd)) {
                throw new IOException("protect(" + fd + ") 失败");
            }
        }

        @Override
        public boolean usePlatformAutoDetectInterfaceControl() {
            return true;
        }

        /**
         * 建立 TUN。地址、路由、DNS、MTU 全部取自 sing-box 的配置（通过 TunOptions 传入），
         * 分应用代理从我们的策略里取。返回 fd 时用 detachFd：之后 fd 归 libbox 管，
         * 我们不能再 close 它。
         */
        @Override
        public int openTun(TunOptions options) throws Exception {
            Builder builder = new Builder();
            builder.setSession(DEFAULT_SESSION);

            RoutePrefixIterator v4 = options.getInet4Address();
            while (v4 != null && v4.hasNext()) {
                RoutePrefix prefix = v4.next();
                builder.addAddress(prefix.address(), prefix.prefix());
            }
            RoutePrefixIterator v6 = options.getInet6Address();
            while (v6 != null && v6.hasNext()) {
                RoutePrefix prefix = v6.next();
                builder.addAddress(prefix.address(), prefix.prefix());
            }

            boolean hasRoute = false;
            RoutePrefixIterator routes4 = options.getInet4RouteAddress();
            while (routes4 != null && routes4.hasNext()) {
                RoutePrefix route = routes4.next();
                builder.addRoute(route.address(), route.prefix());
                hasRoute = true;
            }
            RoutePrefixIterator routes6 = options.getInet6RouteAddress();
            while (routes6 != null && routes6.hasNext()) {
                RoutePrefix route = routes6.next();
                builder.addRoute(route.address(), route.prefix());
                hasRoute = true;
            }
            if (!hasRoute) {
                builder.addRoute(DEFAULT_TUN_ROUTE_V4, 0);
                builder.addRoute(DEFAULT_TUN_ROUTE_V6, 0);
            }

            StringBox dns = options.getDNSServerAddress();
            if (dns != null && dns.getValue() != null && !dns.getValue().isEmpty()) {
                builder.addDnsServer(dns.getValue());
            }
            if (options.getMTU() > 0) {
                builder.setMtu(options.getMTU());
            }
            applySplitTunneling(builder);

            ParcelFileDescriptor pfd = builder.establish();
            if (pfd == null) {
                throw new IOException("establish() 返回 null，可能是另一个 VPN 正占用系统槽位");
            }
            return pfd.detachFd();
        }

        @Override
        public void startDefaultInterfaceMonitor(InterfaceUpdateListener listener) throws Exception {
            startDefaultInterfaceCallback(listener);
        }

        @Override
        public void closeDefaultInterfaceMonitor(InterfaceUpdateListener listener) throws Exception {
            stopDefaultInterfaceCallback();
        }

        /**
         * 接口枚举。返回空：默认网络由 {@link #startDefaultInterfaceCallback} 提供，
         * 我们不做 process/接口级的路由规则，所以不需要完整列表。
         */
        @Override
        public NetworkInterfaceIterator getInterfaces() throws Exception {
            return new NetworkInterfaceIterator() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public io.nekohasekai.libbox.NetworkInterface next() {
                    return null;
                }
            };
        }

        @Override
        public ConnectionOwner findConnectionOwner(int ipProtocol, String sourceAddress,
                                                   int sourcePort, String destinationAddress,
                                                   int destinationPort) throws Exception {
            throw new UnsupportedOperationException("进程级路由规则不支持");
        }

        @Override
        public boolean includeAllNetworks() {
            return false;
        }

        @Override
        public boolean underNetworkExtension() {
            return false;
        }

        @Override
        public boolean useProcFS() {
            return false;
        }

        @Override
        public LocalDNSTransport localDNSTransport() {
            // 返回 null：DNS 由配置里的 udp 服务器经代理解析，不用 libbox 的本地 DNS 通道。
            return null;
        }

        @Override
        public WIFIState readWIFIState() {
            return null;
        }

        @Override
        public StringIterator systemCertificates() {
            return emptyStrings();
        }

        @Override
        public void clearDNSCache() {
        }

        @Override
        public void sendNotification(io.nekohasekai.libbox.Notification notification) {
            LogUtil.i(TAG + ": " + notification.getTitle() + " " + notification.getBody());
        }
    }

    /** CommandServer 的回调。sing-box 请求停止时转到主线程拆除，避免在 Go 回调线程里关闭自己。 */
    private final class CommandHandler implements CommandServerHandler {

        @Override
        public SystemProxyStatus getSystemProxyStatus() {
            SystemProxyStatus status = new SystemProxyStatus();
            status.setAvailable(false);
            status.setEnabled(false);
            return status;
        }

        @Override
        public void serviceReload() {
        }

        @Override
        public void serviceStop() {
            mainHandler.post(() -> {
                teardown();
                stopSelf();
            });
        }

        @Override
        public void setSystemProxyEnabled(boolean enabled) {
        }

        @Override
        public void writeDebugMessage(String message) {
            LogUtil.d(TAG + ": " + message);
        }
    }

    private static StringIterator emptyStrings() {
        return new StringIterator() {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public int len() {
                return 0;
            }

            @Override
            public String next() {
                return null;
            }
        };
    }
}
