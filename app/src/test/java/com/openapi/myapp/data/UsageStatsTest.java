package com.openapi.myapp.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class UsageStatsTest {

    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Shanghai");
    private static final long H = 3600_000L;

    private static long at(int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(TZ);
        c.clear();
        c.set(2026, month - 1, day, hour, minute, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void singleSessionLandsOnToday() {
        List<UsageStats.Span> spans = new ArrayList<>();
        spans.add(new UsageStats.Span(at(11, 12, 9, 0), at(11, 12, 10, 0), 1000, 500));
        UsageStats.Day[] d = UsageStats.lastDays(spans, at(11, 12, 12, 0), 7, TZ);
        assertEquals(7, d.length);
        assertEquals(H, d[6].connectedMs);
        assertEquals(1, d[6].sessions);
        assertEquals(1000, d[6].rxBytes);
        assertEquals(500, d[6].txBytes);
        assertEquals(0, d[5].connectedMs);
    }

    @Test
    public void sessionAcrossMidnightIsSplitByOverlap() {
        List<UsageStats.Span> spans = new ArrayList<>();
        // 23:00 -> 次日 01:00，各占一半
        spans.add(new UsageStats.Span(at(11, 11, 23, 0), at(11, 12, 1, 0), 2000, 1000));
        UsageStats.Day[] d = UsageStats.lastDays(spans, at(11, 12, 12, 0), 7, TZ);
        assertEquals(H, d[5].connectedMs);
        assertEquals(H, d[6].connectedMs);
        assertEquals(1000, d[5].rxBytes);
        assertEquals(1000, d[6].rxBytes);
        // 会话数只算在开始那天，避免一次连接被数成两次
        assertEquals(1, d[5].sessions);
        assertEquals(0, d[6].sessions);
    }

    @Test
    public void oldSessionsOutsideWindowAreIgnored() {
        List<UsageStats.Span> spans = new ArrayList<>();
        spans.add(new UsageStats.Span(at(10, 1, 9, 0), at(10, 1, 10, 0), 9999, 9999));
        UsageStats.Day[] d = UsageStats.lastDays(spans, at(11, 12, 12, 0), 7, TZ);
        long total = 0;
        for (UsageStats.Day day : d) {
            total += day.connectedMs + day.totalBytes() + day.sessions;
        }
        assertEquals(0, total);
    }

    @Test
    public void zeroLengthSessionCountsButHasNoDuration() {
        List<UsageStats.Span> spans = new ArrayList<>();
        spans.add(new UsageStats.Span(at(11, 12, 9, 0), at(11, 12, 9, 0), 10, 20));
        UsageStats.Day[] d = UsageStats.lastDays(spans, at(11, 12, 12, 0), 7, TZ);
        assertEquals(0, d[6].connectedMs);
        assertEquals(1, d[6].sessions);
        assertEquals(10, d[6].rxBytes);
    }

    @Test
    public void nodesAndProtocolsBelongToTheStartDayOnly() {
        List<UsageStats.Span> spans = new ArrayList<>();
        // 跨午夜的一次连接：只算在开始那天，否则"当天连过几个节点"会被数成两倍
        spans.add(new UsageStats.Span(at(11, 11, 23, 0), at(11, 12, 1, 0), 0, 0, "1.2.3.4:443", "WIREGUARD"));
        // 同一天同一节点再连一次：不同节点数不应变成 2
        spans.add(new UsageStats.Span(at(11, 12, 9, 0), at(11, 12, 10, 0), 0, 0, "1.2.3.4:443", "WIREGUARD"));
        spans.add(new UsageStats.Span(at(11, 12, 11, 0), at(11, 12, 12, 0), 0, 0, "5.6.7.8:443", "VLESS_REALITY"));
        UsageStats.Day[] d = UsageStats.lastDays(spans, at(11, 12, 13, 0), 7, TZ);
        assertEquals(1, d[5].nodes.size());
        assertEquals(1, d[5].protocols.size());
        assertEquals(2, d[6].nodes.size());
        assertEquals(2, d[6].protocols.size());
    }

    @Test
    public void missingNodeAndProtocolDoNotCount() {
        List<UsageStats.Span> spans = new ArrayList<>();
        spans.add(new UsageStats.Span(at(11, 12, 9, 0), at(11, 12, 10, 0), 0, 0, null, ""));
        UsageStats.Day[] d = UsageStats.lastDays(spans, at(11, 12, 12, 0), 7, TZ);
        assertEquals(1, d[6].sessions);
        assertEquals(0, d[6].nodes.size());
        assertEquals(0, d[6].protocols.size());
    }

    @Test
    public void formatting() {
        assertEquals("45s", UsageStats.formatDuration(45_000));
        assertEquals("12m", UsageStats.formatDuration(12 * 60_000));
        assertEquals("1h 05m", UsageStats.formatDuration(65 * 60_000));
        assertEquals("512 B", UsageStats.formatBytes(512));
        assertEquals("1.5 KB", UsageStats.formatBytes(1536));
        assertEquals("2.0 MB", UsageStats.formatBytes(2 * 1024 * 1024));
    }
}
