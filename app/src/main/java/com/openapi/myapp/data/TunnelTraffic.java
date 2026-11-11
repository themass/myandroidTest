package com.openapi.myapp.data;

import android.net.TrafficStats;
import android.os.Process;

import androidx.annotation.Nullable;

/**
 * 隧道流量计数。
 * <p>
 * 原来读 {@code /sys/class/net/tunN/statistics}。Android 11+ 的 SELinux 不允许应用读这个目录
 * （实测 Permission denied），read() 一直静默返回 null，数据库里 117 条会话的流量全是 0，
 * 而时长是准的——用户界面上只要展示流量就会一直是 0。
 * <p>
 * 改用 {@link TrafficStats#getUidRxBytes}/{@link TrafficStats#getUidTxBytes} 读本应用 UID 的计数：
 * 三种引擎（IKEv2、WireGuard、sing-box）的隧道外层 socket 都是本进程发出的，
 * 所以 UID 计数 ≈ 隧道对端的收发字节，不需要任何权限。
 * <p>
 * 这是近似值：包含隧道协议自身的封装开销，也包含应用自己的 API 请求。
 * 展示时应当叫"流量"，不要宣称是用户实际浏览的字节数。
 * 计数在进程重启后从 0 开始，调用方已按"计数变小就当作新基线"处理。
 */
final class TunnelTraffic {

    private TunnelTraffic() {
    }

    /** 本应用 UID 的累计 {rx, tx} 字节；系统不支持统计时返回 null。 */
    @Nullable
    static long[] read() {
        int uid = Process.myUid();
        long rx = TrafficStats.getUidRxBytes(uid);
        long tx = TrafficStats.getUidTxBytes(uid);
        if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) {
            return null;
        }
        return new long[]{rx, tx};
    }
}
