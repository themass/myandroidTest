package com.openapi.common.util;

import android.util.Log;

import com.openapi.common.CommonConstants;
import com.openapi.myapp.base.MyApplication;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 错误日志落盘：{@link LogUtil#e} 写入 {@link CommonConstants#BUG_FILE}，
 * 用户点「报告日志」时由 {@code LogUploadService} 连同 charon 日志一起上传。
 * <p>
 * 之前这条链路是断的：{@code LogAddTofile}／{@code ActionLog} 整个被注释掉，
 * {@code LogUtil.e} 只把事件发到 EventBus，没有任何人写文件，上传上去的 log.log 永远是空的。
 * <p>
 * 这里不用 EventBus：写文件是副作用，不该依赖有没有人注册订阅者。
 * 写操作在单个后台线程串行执行，避免主线程 I/O 以及并发写同一文件。
 */
public final class DiagnosticLog {

    private static final String TAG = "DiagnosticLog";
    /** 单文件上限，超过就轮转：保留最近的，避免长期不清理把磁盘写满。 */
    private static final long MAX_BYTES = 512 * 1024;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "diag-log");
        t.setDaemon(true);
        return t;
    });
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private DiagnosticLog() {
    }

    /**
     * 上传前追加一段环境快照：机型、系统、传输类型、底层 MTU、隧道 MTU、协议、最近一次
     * 选中的节点。只有这些信息凑在一起，才能判断"是某个运营商 + 某个协议 + 某个网络"出的问题。
     */
    public static void snapshot(android.content.Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== SNAPSHOT ").append(FMT.format(new Date())).append(" ====\n");
        sb.append("device: ").append(android.os.Build.MANUFACTURER).append(' ')
                .append(android.os.Build.MODEL).append(" / Android ")
                .append(android.os.Build.VERSION.RELEASE).append(" (API ")
                .append(android.os.Build.VERSION.SDK_INT).append(")\n");
        try {
            sb.append("app: ").append(context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName).append('\n');
        } catch (Exception ignored) {
        }
        try {
            com.openapi.data.vpn.engine.TunnelMtuPolicy.Snapshot snap =
                    com.openapi.data.vpn.engine.TunnelMtuPolicy.lastResolved();
            sb.append("net: ").append(com.openapi.data.vpn.engine.TunnelMtuPolicy.networkType(context))
                    .append(" linkMtu=").append(com.openapi.data.vpn.engine.TunnelMtuPolicy.linkMtu(context))
                    .append('\n');
            if (snap != null) {
                sb.append("tunnel: ").append(snap.proto).append(" mtu=").append(snap.mtu)
                        .append(" node=").append(snap.nodeKey).append('\n');
            } else {
                sb.append("tunnel: (never connected)\n");
            }
        } catch (Throwable t) {
            sb.append("net: unavailable (").append(t.getClass().getSimpleName()).append(")\n");
        }
        try {
            com.openapi.data.local.UserNodeStore.UserNode node =
                    com.openapi.data.local.UserNodeStore.selected(context);
            sb.append("selected: ").append(node == null
                    ? "server node" : "custom " + node.protocol + " " + node.server).append('\n');
        } catch (Throwable ignored) {
        }
        append(sb.toString());
    }

    /** 记一条错误。任意线程可调；写盘是异步的。 */
    public static void error(String message, Throwable throwable) {
        final String line = FMT.format(new Date()) + " " + message
                + (throwable == null ? "" : "\n" + Log.getStackTraceString(throwable));
        WORKER.execute(() -> append(line));
    }

    private static void append(String text) {
        android.content.Context context = MyApplication.getInstance();
        if (context == null) {
            return;
        }
        try {
            File file = new File(FileUtils.getBugFilePath());
            File dir = file.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                return;
            }
            if (file.length() > MAX_BYTES) {
                // 简单轮转：只留最近的一半，不做多文件版本
                truncateKeepTail(file, MAX_BYTES / 2);
            }
            Writer w = null;
            try {
                w = new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8");
                w.write(text);
                w.write("\n");
            } finally {
                if (w != null) {
                    try {
                        w.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "写入诊断日志失败: " + e.getMessage());
        }
    }

    private static void truncateKeepTail(File file, long keep) {
        try {
            byte[] all = new byte[(int) file.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                int off = 0;
                int n;
                while (off < all.length && (n = in.read(all, off, all.length - off)) > 0) {
                    off += n;
                }
            }
            int start = (int) Math.max(0, all.length - keep);
            // 从下一个换行开始，避免留下半行
            while (start < all.length && all[start] != '\n') {
                start++;
            }
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write(all, start, all.length - start);
            }
        } catch (Exception e) {
            Log.w(TAG, "轮转诊断日志失败: " + e.getMessage());
        }
    }
}
