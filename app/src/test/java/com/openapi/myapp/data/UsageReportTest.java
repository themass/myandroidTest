package com.openapi.myapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

public class UsageReportTest {

    @Test
    public void dayKeyUsesLocalCalendarAndZeroPads() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.NOVEMBER, 3, 5, 6, 7);
        assertEquals("2026-11-03", UsageReport.dayKey(c.getTimeInMillis()));
    }

    @Test
    public void dayKeyIsStableAcrossNoonAndMidnight() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.NOVEMBER, 3, 23, 59, 59);
        String late = UsageReport.dayKey(c.getTimeInMillis());
        c.add(Calendar.SECOND, 2);
        String next = UsageReport.dayKey(c.getTimeInMillis());
        assertEquals("2026-11-03", late);
        // 跨过午夜必须换成下一天，否则整天的数据会一直记在昨天
        assertTrue(next.compareTo(late) > 0);
    }

    @Test
    public void featureBitsAreDistinct() {
        int[] bits = {
                UsageReport.FEATURE_FAVORITE,
                UsageReport.FEATURE_USAGE_PAGE,
                UsageReport.FEATURE_NET_CHECK,
                UsageReport.FEATURE_SPLIT_TUNNEL,
                UsageReport.FEATURE_KILL_SWITCH,
                UsageReport.FEATURE_REWARD,
                UsageReport.FEATURE_NODE_SWITCH,
        };
        int mask = 0;
        for (int b : bits) {
            assertEquals("bit 必须只有一位", 1, Integer.bitCount(b));
            assertEquals("bit 不能重复", 0, mask & b);
            mask |= b;
        }
    }
}
