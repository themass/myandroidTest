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
    /** 与 IKEv2 共用同一个通知 ID（见 VpnNotifier）。 */
    private static final int FOREGROUND_NOTIFY_ID = com.openapi.data.vpn.VpnNotifier.NOTIFY_ID;
    private static final String NOTIFY_CHANNEL = "SINGBOX_VPN_CHANNEL";
    private static final String DEFAULT_SESSION = "sing-box";
    private static final String DEFAULT_TUN_ROUTE_V4 = "0.0.0.0";
    private static final String DEFAULT_TUN_ROUTE_V6 = "::";

    public static final String ACTION_CONNECT = "com.openapi.singbox.CONNECT";
    public static final String ACTION_DISCONNECT = "com.openapi.singbox.DISCONNECT";
    public static final String EXTRA_CONFIG_JSON = "singbox_config_json";
    public static final String EXTRA_MTU_PROTO = "mtu_proto";
    public static final String EXTRA_MTU_NODE = "mtu_node";
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

    /** 进程内是否有一条 sing-box 隧道在跑（从 connect 到 teardown）。 */
    private static volatile boolean sRunning;

    public static boolean isRunning() {
        return sRunning;
    }

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
    /** 每次连接加一。旧连接的探针线程据此判断自己是否已过期，避免给新连接报错误的结果。 */
    private volatile int generation;
    @Nullable
    private String splitMode;
    private String mtuProto;
    private String mtuNode;
    @Nullable
    private String[] splitPackages;
    @Nullable
    private ConnectivityManager.NetworkCallback defaultNetworkCallback;
    /**
     * 当前 TUN。libbox 只 dup 一份自己用，原始 fd 归我们：拆除时必须关掉，
     * 否则系统 VPN 一直挂着、流量全进一个没人读的 TUN，整机断网（只能杀进程恢复）。
     */
    @Nullable
    private ParcelFileDescriptor tunFd;

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
        // 先显示"连接中"。探针通过后才改成"已连接"，否则通知栏会比 App 先说连上了。
        startForeground(FOREGROUND_NOTIFY_ID, com.openapi.data.vpn.VpnNotifier.current(this));

        String configJson = intent.getStringExtra(EXTRA_CONFIG_JSON);
        splitMode = intent.getStringExtra(EXTRA_SPLIT_MODE);
        mtuProto = intent.getStringExtra(EXTRA_MTU_PROTO);
        mtuNode = intent.getStringExtra(EXTRA_MTU_NODE);
        splitPackages = intent.getStringArrayExtra(EXTRA_SPLIT_PACKAGES);
        if (configJson == null) {
            LogUtil.e(TAG + ": 启动参数缺失 config");
            notifyFailed("missing config");
            stopForeground(false);
            stopSelf();
            return;
        }

        // 上一条还没拆（自动重连、换下一个候选节点）：先停掉，否则旧的 libbox 和 TUN 一直挂着。
        running = false;
        stopCore();
        running = true;
        sRunning = true;
        int gen = ++generation;
        // startOrReloadService 会同步回调 openTun 并等待内核就绪，不能放在主线程上，否则 ANR。
        new Thread(() -> startService(configJson, gen), "singbox-start").start();
    }

    private void startService(String configJson, int gen) {
        try {
            ensureLibboxSetup();
            commandServer = Libbox.newCommandServer(commandHandler, platform);
            commandServer.start();
            commandServer.startOrReloadService(configJson, new OverrideOptions());
            LogUtil.i(TAG + ": sing-box 已启动，开始连通性探测");
            new Thread(() -> probeThenNotify(gen), "singbox-probe").start();
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            LogUtil.e(TAG + ": sing-box 启动失败: " + reason);
            mainHandler.post(() -> {
                if (gen != generation) {
                    return;
                }
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
    private void probeThenNotify(int gen) {
        TunnelProbe.Result res = TunnelProbe.probeUntilDeadline(this, () -> running && gen == generation);
        if (!running || gen != generation) {
            LogUtil.i(TAG + ": 探针中止，隧道已拆除或已被新连接替换");
            return;
        }
        if (res == TunnelProbe.Result.OK) {
            LogUtil.i(TAG + ": 连通性探针通过（经 VPN 网络）");
            notifyEstablished();
            return;
        }
        String reason = "隧道已建立但探测流量不通（握手失败或节点参数不对）";
        if (res == TunnelProbe.Result.MTU_SUSPECT && mtuProto != null && mtuNode != null) {
            // 疑似 MTU 黑洞：记下本网络对该节点用 1280，下次连接直接用。本次不自动重试（见文档）。
            com.openapi.data.vpn.engine.TunnelMtuPolicy.Proto p =
                    com.openapi.data.vpn.engine.TunnelMtuPolicy.Proto.valueOf(mtuProto);
            if (p != com.openapi.data.vpn.engine.TunnelMtuPolicy.Proto.STREAM) {
                com.openapi.data.vpn.engine.TunnelMtuPolicy.learn(p, mtuNode,
                        com.openapi.data.vpn.engine.TunnelMtuPolicy.FLOOR);
                LogUtil.i(TAG + ": 疑似 MTU 黑洞，已记录 MTU=1280");
                reason = "疑似 MTU 不匹配，降到 1280 重连";
            }
        }
        LogUtil.e(TAG + ": " + reason);
        final String failReason = reason;
        mainHandler.post(() -> {
            if (!running || gen != generation) {
                return;
            }
            notifyFailed(failReason);
            teardown();
            stopSelf();
        });
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
        sRunning = false;
        stopCore();
        stopForeground(false);
        notifyStopped();
    }

    /** 停 libbox、关 TUN，不发通知。重连/换节点时复用同一个 Service，先把上一条拆干净。 */
    private void stopCore() {
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
        closeTun();
    }

    private synchronized void closeTun() {
        ParcelFileDescriptor pfd = tunFd;
        tunFd = null;
        if (pfd != null) {
            try {
                pfd.close();
            } catch (IOException e) {
                LogUtil.e(TAG + ": 关闭 TUN 异常: " + e.getMessage());
            }
        }
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
            // 不能 detachFd：libbox 会 dup 自己的一份，原始 fd 由我们持有并在 teardown 时关闭。
            closeTun();
            tunFd = pfd;
            return pfd.getFd();
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
         * 接口枚举：sing-box 按名字找默认网络接口（wlan0 / rmnet）来绑定出口 socket。
         * 返回空列表时它找不到接口，所有出站都报 "no available network interface"，
         * 表现为隧道建立了但一个包都发不出去，整机断网。做法与官方 SFA 一致：列出所有非 VPN 网络。
         */
        @Override
        public NetworkInterfaceIterator getInterfaces() throws Exception {
            java.util.List<io.nekohasekai.libbox.NetworkInterface> list = new java.util.ArrayList<>();
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            if (cm != null) {
                for (Network network : cm.getAllNetworks()) {
                    io.nekohasekai.libbox.NetworkInterface nif = toLibboxInterface(cm, network);
                    if (nif != null) {
                        list.add(nif);
                    }
                }
            }
            java.util.Iterator<io.nekohasekai.libbox.NetworkInterface> it = list.iterator();
            return new NetworkInterfaceIterator() {
                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public io.nekohasekai.libbox.NetworkInterface next() {
                    return it.next();
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

    /** Go net.Flags 的位定义（sing-box 用的就是这一套）。 */
    private static final int GO_FLAG_UP = 1;
    private static final int GO_FLAG_BROADCAST = 1 << 1;
    private static final int GO_FLAG_LOOPBACK = 1 << 2;
    private static final int GO_FLAG_POINT_TO_POINT = 1 << 3;
    private static final int GO_FLAG_MULTICAST = 1 << 4;
    private static final int GO_FLAG_RUNNING = 1 << 5;

    @Nullable
    private static io.nekohasekai.libbox.NetworkInterface toLibboxInterface(ConnectivityManager cm,
                                                                           Network network) {
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        LinkProperties lp = cm.getLinkProperties(network);
        if (caps == null || lp == null || lp.getInterfaceName() == null
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return null;
        }
        java.net.NetworkInterface jnif;
        try {
            jnif = java.net.NetworkInterface.getByName(lp.getInterfaceName());
        } catch (IOException e) {
            return null;
        }
        if (jnif == null) {
            return null;
        }
        io.nekohasekai.libbox.NetworkInterface nif = new io.nekohasekai.libbox.NetworkInterface();
        nif.setName(lp.getInterfaceName());
        nif.setIndex(jnif.getIndex());
        try {
            nif.setMTU(jnif.getMTU());
        } catch (IOException ignored) {
        }
        java.util.List<String> addrs = new java.util.ArrayList<>();
        for (android.net.LinkAddress la : lp.getLinkAddresses()) {
            addrs.add(la.getAddress().getHostAddress() + "/" + la.getPrefixLength());
        }
        nif.setAddresses(strings(addrs));
        int flags = 0;
        try {
            if (jnif.isUp()) {
                flags |= GO_FLAG_UP | GO_FLAG_RUNNING;
            }
            if (jnif.isLoopback()) {
                flags |= GO_FLAG_LOOPBACK;
            }
            if (jnif.isPointToPoint()) {
                flags |= GO_FLAG_POINT_TO_POINT;
            } else {
                flags |= GO_FLAG_BROADCAST;
            }
            if (jnif.supportsMulticast()) {
                flags |= GO_FLAG_MULTICAST;
            }
        } catch (IOException ignored) {
        }
        nif.setFlags(flags);
        int type = Libbox.InterfaceTypeOther;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            type = Libbox.InterfaceTypeWIFI;
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            type = Libbox.InterfaceTypeCellular;
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            type = Libbox.InterfaceTypeEthernet;
        }
        nif.setType(type);
        java.util.List<String> dns = new java.util.ArrayList<>();
        for (java.net.InetAddress a : lp.getDnsServers()) {
            dns.add(a.getHostAddress());
        }
        nif.setDNSServer(strings(dns));
        nif.setMetered(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
        return nif;
    }

    private static StringIterator strings(java.util.List<String> values) {
        java.util.Iterator<String> it = values.iterator();
        int size = values.size();
        return new StringIterator() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public int len() {
                return size;
            }

            @Override
            public String next() {
                return it.next();
            }
        };
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
