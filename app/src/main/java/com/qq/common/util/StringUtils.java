package com.qq.common.util;

import android.text.TextUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by sssong on 14-03-12.
 */
public class StringUtils {
    public static final String DEFAULT_VALUE = "-";
    private static final String PERCENT_TAG = "%";
    private static final String SCALE_WAN = "万";
    private static final String SCALE_YI = "亿";
    private static final String SCALE_WAN_YI = "万亿";
    private static final int ONE = 1;
    private static final int WAN = 10000;
    private static final int YI = 100000000;
    private static final long WAN_YI = 1000000000000L;

    /**
     * 格式化百分比，默认：正数不保留+号，不自动转换单位
     *
     * @param value     百分比原数值
     * @param precision 小数精度位数
     * @return String
     */
    public static String formatPercent(Double value, int precision) {
        return formatPercent(value, precision, false, false);
    }

    /**
     * 格式化数字，默认：正数不保留+号，自动转换单位
     *
     * @param value     百分比原数值
     * @param precision 小数精度位数
     * @return String
     */
    public static String formatDouble(Double value, int precision) {
        return formatDouble(value, precision, false, true);
    }

    /**
     * 格式化百分比
     *
     * @param value               百分比原数值
     * @param precision           小数精度位数
     * @param isAlwaryIncludeSign 正数是否也保留+号
     * @param isScale             是否自动转换单位，如：12000 变成 1.2万
     * @return String
     */
    public static String formatPercent(Double value, int precision,
                                       boolean isAlwaryIncludeSign, boolean isScale) {
        if (value == null) {
            return DEFAULT_VALUE;
        }
        return formatDouble(value * 100, precision, isAlwaryIncludeSign,
                isScale) + PERCENT_TAG;
    }

    /**
     * 格式化数字
     *
     * @param value               百分比原数值
     * @param precision           小数精度位数
     * @param isAlwaryIncludeSign 正数是否也保留+号
     * @param isScale             是否自动转换单位，如：12000 变成 1.2万
     * @return String
     */
    public static String formatDouble(Double value, int precision,
                                      boolean isAlwaryIncludeSign, boolean isScale) {
        if (value == null) {
            return DEFAULT_VALUE;
        }

        StringBuilder format = new StringBuilder();
        format.append("%");
        if (isAlwaryIncludeSign) {
            format.append("+");
        }
        format.append(".").append(precision).append("f");

        if (isScale) {
            double absValue = Math.abs(value);
            if (absValue >= WAN_YI) {
                value = value / WAN_YI;
                format.append(SCALE_WAN_YI);
            } else if (absValue >= YI) {
                value = value / YI;
                format.append(SCALE_YI);
            } else if (absValue >= WAN) {
                value = value / WAN;
                format.append(SCALE_WAN);
            }
        }

        return String.format(format.toString(), value);
    }

    public static String formatInt(Double value, int precision) {
        if (value == null) {
            return DEFAULT_VALUE;
        }
        StringBuilder format = new StringBuilder();
        format.append("%");
        double absValue = Math.abs(value);
        if (absValue >= WAN_YI) {
            value = value / WAN_YI;
            format.append(".").append(precision).append("f");
            format.append(SCALE_WAN_YI);
        } else if (absValue >= YI) {
            value = value / YI;
            format.append(".").append(precision).append("f");
            format.append(SCALE_YI);
        } else if (absValue >= WAN) {
            value = value / WAN;
            format.append(".").append(precision).append("f");
            format.append(SCALE_WAN);
        } else {
            return String.valueOf(value.longValue());
        }
        return String.format(format.toString(), value);
    }

    public static String formatWithScale(Double value, int precision,
                                         long scale, boolean showScale) {
        if (value == null) {
            return DEFAULT_VALUE;
        }

        StringBuilder format = new StringBuilder();
        format.append("%");
        format.append(".").append(precision).append("f");
        value = value / scale;
        if (showScale) {
            if (scale == WAN_YI) {
                format.append(SCALE_WAN_YI);
            } else if (scale == YI) {
                format.append(SCALE_YI);
            } else if (scale == WAN) {
                format.append(SCALE_WAN);
            }
        }
        return String.format(format.toString(), value);
    }

    public static String formatIntWithScale(Double value, int precision,
                                            long scale, boolean showScale) {
        if (value == null) {
            return DEFAULT_VALUE;
        }
        if (scale == ONE) {
            return String.valueOf(value.longValue());
        }
        StringBuilder format = new StringBuilder();
        format.append("%");
        format.append(".").append(precision).append("f");
        value = value / scale;
        if (showScale) {
            if (scale == WAN_YI) {
                format.append(SCALE_WAN_YI);
            } else if (scale == YI) {
                format.append(SCALE_YI);
            } else if (scale == WAN) {
                format.append(SCALE_WAN);
            }
        }
        return String.format(format.toString(), value);
    }

    public static String getScaleText(long scale) {
        if (scale == WAN_YI) {
            return SCALE_WAN_YI;
        } else if (scale == YI) {
            return SCALE_YI;
        } else if (scale == WAN) {
            return SCALE_WAN;
        }
        return "";
    }

    /**
     * 根据一组数据，动态获取缩放的大小，如果缩放后两个值一样，则降一级（比如从亿降到万）
     *
     * @param values values
     * @return scale
     */
    public static long getDynamicScale(Double... values) {
        boolean hitWanYi = false;
        boolean hitYi = false;
        boolean hitWan = false;
        for (Double v : values) {
            if (v != null) {
                v = Math.abs(v);
                if (v > WAN_YI) {
                    hitWanYi = true;
                }
                if (v > YI) {
                    hitYi = true;
                }
                if (v > WAN) {
                    hitWan = true;
                }
            }
        }
        long scale = ONE;
        if (hitWanYi) {
            scale = WAN_YI;
        } else if (hitYi) {
            scale = YI;
        } else if (hitWan) {
            scale = WAN;
        }
        // 检查是否需要降级
        boolean needDownScale = false;
        Set<Long> checkSet = new HashSet<>();
        if (scale != ONE) {
            for (Double v : values) {
                if (v != null) {
                    long intV = (long) ((v / scale) * 100);
                    if (checkSet.contains(intV)) {
                        needDownScale = true;
                        break;
                    }
                    checkSet.add(intV);
                }
            }
        }
        if (needDownScale) {
            if (scale == WAN_YI) {
                scale = YI;
            } else if (scale == YI) {
                scale = WAN;
            } else {
                scale = ONE;
            }
        }
        return scale;
    }

    /**
     * 大小写转换 大写变小写 小写边大写 只针对key(全部为大写 或全部为小写)
     */
    public static String upLow(String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        String covString;
        Pattern p = Pattern.compile("[A-Z]+");
        Matcher m1 = p.matcher(key); // 判断是否含有大写字符
        if (m1.find()) {
            covString = key.toLowerCase();// 大变小
        } else {
            covString = key.toUpperCase();
        }
        return covString;

    }

    /**
     * 把带分隔符的字符串 转换为数据
     *
     * @param string
     * @param divisionChar 分隔符
     * @return
     */
    public static String[] stringAnalytical(String string, String divisionChar) {
        int i = 0;
        StringTokenizer tokenizer = new StringTokenizer(string, divisionChar);
        String[] str = new String[tokenizer.countTokens()];
        while (tokenizer.hasMoreTokens()) {
            str[i] = tokenizer.nextToken();
            i++;
        }
        return str;
    }

    public static boolean hasText(String str) {
        return str != null && str.trim().length() > 0;
    }

    public static String deleteHtmlLabel(String content) {
        if (content == null) {
            return null;
        }
        return content.replaceAll("\r", "").replaceAll("\n", "")
                .replaceAll("<(.*?)>", "").replaceAll(" ", "");
    }

    public static int str2Int(String str) {
        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            return 0;
        }
    }

    public static String stackTrace2String(Throwable e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer, true));
        return writer.toString();
    }

    public static boolean isEmpty(String name) {
        return name==null || name.trim().length()==0;
    }
}
