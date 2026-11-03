package com.openapi.data.vpn.engine;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.openapi.common.util.LogUtil;
import com.openapi.domain.model.SplitTunnelingPolicy;
import com.openapi.ks.ui.main.MainFragmentViewPage;
import com.openapi.ks.free1.R;

import org.json.JSONObject;

/**
 * VLESS+Reality 专用的 {@link VpnService}。
 * <p>
 * libXray（Xray-core 的 gomobile 封装）本身不实现 Android 的 {@code VpnService}——
 * 它只接受一个已经建立好的 TUN 文件描述符（通过 {@code XRAY_TUN_FD} 环境变量传入），
 * 三层路由、地址分配仍由我们自己通过 {@link VpnService.Builder} 完成。
 * <p>
 * 这是继 {@code CharonVpnService}（IKEv2）、{@code GoBackend$VpnService}（WireGuard）
 * 之后第三个 VpnService 组件。三者在 manifest 里共存没有问题——Android 的限制是
 * "运行时同一时刻只能有一个 TUN 处于建立状态"，不是"一个 App 只能声明一个组件"。
 * 真正的互斥由 {@code CharonVpnSessionRepository.connect()} 保证：切换协议前
 * 先停掉旧引擎。
 */
public final class XrayVpnService extends VpnService {

    private static final String TAG = "XrayVpnService";
    /**
     * 与 {@code CharonVpnService.FOREGROUND_NOTIFY_ID}（100）错开，避免切换协议时
     * 两个 VpnService 的通知短暂并存导致互相覆盖。
     */
    private static final int FOREGROUND_NOTIFY_ID = 200;
    private static final String NOTIFY_CHANNEL = "XRAY_VPN_CHANNEL";
    private static final String DEFAULT_DNS = "1.1.1.1,8.8.8.8";

    /**
     * 连通性探针：总时长 / 单次超时 / 间隔 / 目标。
     * <p>
     * 目标故意用裸 IP 而不是域名——域名会先走 DNS，DNS 失败和隧道不通分不清；
     * 直接 TCP 到公网 IP 才能单独验证"包能不能穿过隧道到外面"。
     * 选三个不同运营商的大厂 anycast 地址，避免单个目标被机房出站策略拦掉误判。
     */
    private static final long PROBE_TIMEOUT_MS = 20_000L;
    private static final int PROBE_CONNECT_TIMEOUT_MS = 3_000;
    private static final long PROBE_INTERVAL_MS = 1_000L;
    private static final String[][] PROBE_TARGETS = {
            {"1.1.1.1", "443"},
            {"8.8.8.8", "443"},
            {"9.9.9.9", "443"},
    };

    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    public static final String ACTION_CONNECT = "com.openapi.xray.CONNECT";
    public static final String ACTION_DISCONNECT = "com.openapi.xray.DISCONNECT";
    public static final String EXTRA_XRAY_CONFIG_JSON = "xray_config_json";
    public static final String EXTRA_TUN_ADDRESS = "tun_address";
    public static final String EXTRA_DNS = "dns";
    public static final String EXTRA_MTU = "mtu";
    public static final String EXTRA_SPLIT_MODE = "split_mode";
    public static final String EXTRA_SPLIT_PACKAGES = "split_packages";

    /** 进程内唯一监听者；这个服务只服务于一条 VLESS+Reality 隧道，不需要多播。 */
    public interface Listener {
        void onEstablished();

        void onFailed(String reason);

        void onStopped();
    }

    @Nullable
    private static volatile Listener listener;

    public static void setListener(@Nullable Listener l) {
        listener = l;
    }

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
        // 必须在整个建连流程最前面调用：没有前台通知，像华为 EMUI/HarmonyOS
        // 这类对后台进程管控激进的系统会在 TUN 刚建立后的几百毫秒内就把这个
        // Service 杀掉——现象就是"应用内状态显示已连接（绿色），系统状态栏却
        // 没有 VPN 小钥匙图标，也完全没有真实流量"。IKEv2 那条路径
        // （CharonVpnService）一直都有这个前台通知，之前只有这条 VLESS 路径漏掉了。
        startForeground(FOREGROUND_NOTIFY_ID, buildNotification());

        String xrayConfigJson = intent.getStringExtra(EXTRA_XRAY_CONFIG_JSON);
        String tunAddress = intent.getStringExtra(EXTRA_TUN_ADDRESS);
        String dns = intent.getStringExtra(EXTRA_DNS);
        int mtu = intent.getIntExtra(EXTRA_MTU, 1500);
        String splitMode = intent.getStringExtra(EXTRA_SPLIT_MODE);
        String[] splitPackages = intent.getStringArrayExtra(EXTRA_SPLIT_PACKAGES);

        if (xrayConfigJson == null || tunAddress == null) {
            LogUtil.e(TAG + ": 启动参数缺失 config=" + (xrayConfigJson != null)
                    + " tunAddress=" + (tunAddress != null));
            notifyFailed("missing config");
            stopForeground(true);
            stopSelf();
            return;
        }

        try {
            Builder builder = new Builder();
            builder.setSession("VLESS+Reality");
            String[] addrParts = tunAddress.split("/");
            int prefix = addrParts.length > 1 ? Integer.parseInt(addrParts[1]) : 30;
            builder.addAddress(addrParts[0], prefix);
            builder.addRoute("0.0.0.0", 0);
            builder.addRoute("::", 0);
            // 没有 DNS 时应用会去查局域网 DNS（如 192.168.0.1），这个包被全局路由送进
            // 隧道，在远端服务器上不可达——表现为"连上了但任何网站都打不开"。
            if (dns == null || dns.trim().isEmpty()) {
                dns = DEFAULT_DNS;
            }
            for (String d : dns.split(",")) {
                if (!d.trim().isEmpty()) {
                    builder.addDnsServer(d.trim());
                }
            }
            builder.setMtu(mtu);
            applySplitTunneling(builder, splitMode, splitPackages);

            ParcelFileDescriptor fd = builder.establish();
            if (fd == null) {
                String reason = "establish() 返回 null，可能是另一个 VPN 正占用系统槽位";
                LogUtil.e(TAG + ": " + reason);
                notifyFailed(reason);
                stopForeground(true);
                stopSelf();
                return;
            }
            tunFd = fd;

            // fd 必须写进 Xray 配置根部的 env 对象，不能用 Os.setenv：Go 在 Android
            // 共享库模式下以空环境启动（rt0_android_*_lib 的 envv 为空），只认 Go 侧
            // os.Setenv，Java 设置的进程环境变量它永远读不到，会回落到 fd 0（/dev/null），
            // 表现为 "[tun] failed to read a packet ... EOF"。见 libXray README。
            JSONObject xrayConfig = new JSONObject(xrayConfigJson);
            xrayConfig.put("env", new JSONObject()
                    .put("xray.tun.fd", String.valueOf(fd.getFd())));

            // Xray 连服务器的 socket 必须 protect，否则它也会被 0.0.0.0/0 路由塞回 TUN，
            // 形成环路：隧道"已建立"但没有任何流量能出去。
            libXray.LibXray.registerDialerController(socketFd -> protect((int) socketFd));

            JSONObject payload = new JSONObject().put("xrayJson", xrayConfig.toString());
            JSONObject envelope = new JSONObject()
                    .put("apiVersion", libXray.LibXray.LibXrayAPIVersion)
                    .put("method", "runXray")
                    .put("payload", payload);
            String responseStr = libXray.LibXray.invoke(envelope.toString());
            JSONObject response = new JSONObject(responseStr);
            boolean success = response.optBoolean("success", false);
            if (success) {
                // runXray 返回成功只代表"内核起来了"，不代表流量能通。
                // 之前直接在这里 notifyEstablished()，于是 TUN 建好、内核启动的瞬间
                // UI 就显示"已连接"（雷达变绿），无论 Reality 握手是否真的成功——
                // 这正是"点了就显示成功"的原因。这里补一次真实的连通性探针：
                // 只有探针能通过隧道把包发出去并收到回应，才算真的连上。
                //
                // 探针必须在后台线程跑（最长 PROBE_TIMEOUT_MS），
                // doConnect 由 onStartCommand 在主线程调用，阻塞会直接 ANR。
                LogUtil.i(TAG + ": Xray 内核已启动，开始连通性探测");
                new Thread(this::probeThenNotify, "xray-probe").start();
            } else {
                String err = response.optString("error", "unknown error");
                LogUtil.e(TAG + ": Xray 启动失败: " + err);
                notifyFailed(err);
                teardown();
                stopSelf();
            }
        } catch (Exception e) {
            LogUtil.e(TAG + ": 连接异常: " + e.getMessage());
            notifyFailed(e.getMessage());
            teardown();
            stopSelf();
        }
    }

    /**
     * 真实连通性探针（后台线程）：反复尝试直连几个公网 IP，任意一个能建立
     * TCP 连接就认为隧道通了。全部失败则判定这次连接无效并拆除。
     * <p>
     * 为什么探针有效：探针 socket 由本进程（非 Xray）发起，但此时 TUN 已建立、
     * 0.0.0.0/0 已路由进隧道，所以包必然走 Xray → REALITY → 服务器 → 公网。
     * 服务器那边握手失败或账号/参数不对，连接根本建不起来，这里会一直失败。
     * 只有真正的端到端通路才可能成功。
     */
    private void probeThenNotify() {
        long deadline = System.currentTimeMillis() + PROBE_TIMEOUT_MS;
        int round = 0;
        while (System.currentTimeMillis() < deadline) {
            if (tunFd == null) {
                // 期间已被拆除（用户主动断开或 onDestroy），不要再回调。
                LogUtil.i(TAG + ": 探针中止，隧道已拆除");
                return;
            }
            round++;
            for (String[] target : PROBE_TARGETS) {
                if (probeOnce(target[0], Integer.parseInt(target[1]))) {
                    LogUtil.i(TAG + ": 连通性探针通过（第 " + round + " 轮，"
                            + target[0] + ":" + target[1] + "）");
                    if (tunFd != null) {
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
        String reason = "隧道已建立但探测流量不通（Reality 握手失败或节点参数不对）";
        LogUtil.e(TAG + ": " + reason);
        mainHandler.post(() -> {
            if (tunFd == null) {
                return;
            }
            notifyFailed(reason);
            teardown();
            stopSelf();
        });
    }

    /** 一次 TCP 直连探测；成功返回 true。 */
    private static boolean probeOnce(String host, int port) {
        java.net.Socket socket = new java.net.Socket();
        try {
            socket.connect(new java.net.InetSocketAddress(host, port), PROBE_CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** 分应用代理。与 IKEv2 引擎的做法一致：include/exclude 互斥，
     * 逐包名 try/catch，已卸载的应用跳过而不是让整次连接失败。
     */
    private void applySplitTunneling(Builder builder, @Nullable String splitMode,
                                     @Nullable String[] packages) {
        if (packages == null || packages.length == 0) {
            return;
        }
        boolean include = SplitTunnelingPolicy.Mode.INCLUDE.name().equalsIgnoreCase(splitMode);
        for (String pkg : packages) {
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
        stopForeground(true);
        try {
            JSONObject envelope = new JSONObject()
                    .put("apiVersion", libXray.LibXray.LibXrayAPIVersion)
                    .put("method", "stopXray");
            libXray.LibXray.invoke(envelope.toString());
        } catch (Exception e) {
            LogUtil.e(TAG + ": stopXray 异常: " + e.getMessage());
        }
        if (tunFd != null) {
            try {
                tunFd.close();
            } catch (Exception ignored) {
            }
            tunFd = null;
        }
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

    /**
     * 构造前台服务通知。与 {@code CharonVpnService} 那套富交互通知（RemoteViews、
     * 点击切换等）不同，这里只做最小实现——目的只是让系统承认这是一个前台服务，
     * 不被后台进程回收策略清掉，不需要复刻那一整套 UI。
     */
    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFY_CHANNEL, "VLESS+Reality", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("VLESS+Reality 隧道运行状态");
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
                .setContentText("VLESS+Reality")
                .setSmallIcon(R.drawable.vpn_on)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSound(null)
                .setVibrate(new long[]{0})
                .build();
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
}
