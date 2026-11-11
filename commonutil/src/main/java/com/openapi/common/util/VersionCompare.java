package com.openapi.common.util;

/**
 * 版本号比较：按点分段，每段按数字比较，所以 "2.10" > "2.9"（字符串比较会得到相反的结果）。
 * 缺少的段按 0 处理（"2" 等于 "2.0"）；段里数字后面的非数字部分忽略（"1.2b" 按 "1.2"）。
 */
public final class VersionCompare {

    private VersionCompare() {
    }

    /** 与 Comparable 一致：a 比 b 小返回负数，相等返回 0，大返回正数。null 或空视为 0。 */
    public static int compare(String a, String b) {
        long[] pa = segments(a);
        long[] pb = segments(b);
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            long va = i < pa.length ? pa[i] : 0L;
            long vb = i < pb.length ? pb[i] : 0L;
            if (va != vb) {
                return va < vb ? -1 : 1;
            }
        }
        return 0;
    }

    private static long[] segments(String version) {
        if (version == null || version.trim().isEmpty()) {
            return new long[]{0L};
        }
        String[] parts = version.trim().split("\\.");
        long[] out = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = leadingNumber(parts[i]);
        }
        return out;
    }

    private static long leadingNumber(String segment) {
        int end = 0;
        while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0L;
        }
        try {
            return Long.parseLong(segment.substring(0, end));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
