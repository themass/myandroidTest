package com.openapi.myapp.data;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * 把连接会话按天汇总：已连接时长、会话数、流量。
 * <p>
 * 纯计算，不碰 Android 和数据库，便于单元测试。跨午夜的会话按重叠时长拆到两天里；
 * 流量没有按天的采样，按时长占比分摊（会话本身最长也就几小时，误差可以接受）。
 */
public final class UsageStats {

    /** 一个会话的最小描述，避免依赖 greenDAO 实体。 */
    public static final class Span {
        public final long startAt;
        public final long endAt;
        public final long rxBytes;
        public final long txBytes;
        /** 节点地址与协议名，只用于"当天连过几个节点、用过哪些协议"，可以为 null。 */
        public final String node;
        public final String protocol;

        public Span(long startAt, long endAt, long rxBytes, long txBytes) {
            this(startAt, endAt, rxBytes, txBytes, null, null);
        }

        public Span(long startAt, long endAt, long rxBytes, long txBytes, String node, String protocol) {
            this.startAt = startAt;
            this.endAt = endAt;
            this.rxBytes = rxBytes;
            this.txBytes = txBytes;
            this.node = node;
            this.protocol = protocol;
        }
    }

    /** 某一天的汇总。 */
    public static final class Day {
        /** 当天 00:00 的毫秒时间戳（按传入的时区）。 */
        public final long dayStart;
        public long connectedMs;
        public int sessions;
        public long rxBytes;
        public long txBytes;
        /** 当天连过的不同节点 / 用过的协议。用于上报，不上界面。 */
        public final java.util.Set<String> nodes = new java.util.LinkedHashSet<>();
        public final java.util.Set<String> protocols = new java.util.LinkedHashSet<>();

        Day(long dayStart) {
            this.dayStart = dayStart;
        }

        public long totalBytes() {
            return rxBytes + txBytes;
        }
    }

    private UsageStats() {
    }

    /**
     * 最近 {@code days} 天（含今天）的汇总，下标 0 是最早的一天，最后一个是今天。
     */
    @NonNull
    public static Day[] lastDays(@NonNull List<Span> spans, long nowMs, int days, @NonNull TimeZone tz) {
        Day[] result = new Day[days];
        long today = startOfDay(nowMs, tz);
        for (int i = 0; i < days; i++) {
            result[i] = new Day(addDays(today, i - (days - 1), tz));
        }
        for (Span s : spans) {
            long end = s.endAt > s.startAt ? s.endAt : s.startAt;
            long total = end - s.startAt;
            for (int i = 0; i < days; i++) {
                long from = result[i].dayStart;
                long to = i + 1 < days ? result[i + 1].dayStart : addDays(from, 1, tz);
                long overlap = Math.min(end, to) - Math.max(s.startAt, from);
                boolean startsHere = s.startAt >= from && s.startAt < to;
                if (overlap <= 0 && !startsHere) {
                    continue;
                }
                if (overlap > 0) {
                    result[i].connectedMs += overlap;
                }
                if (startsHere) {
                    result[i].sessions++;
                    // 节点与协议按"开始那天"归属：同一次连接不该在两个不同的日子里各算一个新的
                    if (s.node != null && !s.node.isEmpty()) {
                        result[i].nodes.add(s.node);
                    }
                    if (s.protocol != null && !s.protocol.isEmpty()) {
                        result[i].protocols.add(s.protocol);
                    }
                }
                if (total > 0 && overlap > 0) {
                    result[i].rxBytes += s.rxBytes * overlap / total;
                    result[i].txBytes += s.txBytes * overlap / total;
                } else if (startsHere) {
                    result[i].rxBytes += s.rxBytes;
                    result[i].txBytes += s.txBytes;
                }
            }
        }
        return result;
    }

    public static long startOfDay(long ms, @NonNull TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 加减天数用日历运算，夏令时当天不是 24 小时。 */
    private static long addDays(long dayStart, int delta, @NonNull TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(dayStart);
        c.add(Calendar.DAY_OF_MONTH, delta);
        return c.getTimeInMillis();
    }

    /** 时长格式化：1h 05m / 12m / 45s。 */
    @NonNull
    public static String formatDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) {
            return (s / 3600) + "h " + String.format(java.util.Locale.US, "%02d", (s % 3600) / 60) + "m";
        }
        if (s >= 60) {
            return (s / 60) + "m";
        }
        return s + "s";
    }

    /** 流量格式化：B / KB / MB / GB。 */
    @NonNull
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double v = bytes;
        String[] unit = {"KB", "MB", "GB", "TB"};
        int i = -1;
        while (v >= 1024 && i < unit.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(java.util.Locale.US, v >= 100 ? "%.0f %s" : "%.1f %s", v, unit[i]);
    }
}
