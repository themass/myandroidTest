package com.openapi.myapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.common.util.GsonUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.myapp.bean.form.UsageDailyForm;
import com.openapi.myapp.bean.vo.NullReturnVo;
import com.openapi.myapp.constant.Constants;
import com.openapi.yewu.net.request.CommonResponse;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * 每日使用汇总的上报（留存与时长分析的唯一数据来源）。
 * <p>
 * 报什么：某一天连了多久、连了几次、用了多少流量、连过几个节点、哪些功能被用过。
 * 不报：访问了什么网站、DNS 查询、界面点击流水、IP 之外的任何标识。
 * <p>
 * 数据本来就以连接会话的形式存在本机（{@code ConnSession}），这里只是按天汇总后发出，
 * 不需要额外埋点，也不会因为埋点漏写而统计失真。
 * <p>
 * 上报节奏：连接状态变化时（断开、切节点）尝试一次，另外每天首次打开 App 时补发一次，
 * 保证"昨天用了今天能收到"。最近 7 天一起发，服务端按 (devId, day) 覆盖，
 * 所以重复上报只会刷新数字，不需要客户端维护"哪几天已经发过"。
 */
public final class UsageReport {

    /** 一次补传多少天。留一点余量，覆盖"出去玩了几天没开 App"。 */
    private static final int DAYS = 7;

    /** 两次上报的最小间隔：断开重连很频繁时不要每次都打一遍接口。 */
    private static final long MIN_INTERVAL_MS = 10 * 60_000L;

    private static final String PREF = "usage_report";
    private static final String KEY_LAST_AT = "lastAt";
    private static final String KEY_LAST_TRY = "lastTry";
    private static final String KEY_MASK = "mask_";        // + yyyyMMdd
    private static final String KEY_INFLIGHT = "inflight";

    /** 功能使用位。与服务端 usage_daily.featureMask 的位序一致，加新位只能往后追加。 */
    public static final int FEATURE_FAVORITE = 1;
    public static final int FEATURE_USAGE_PAGE = 1 << 1;
    public static final int FEATURE_NET_CHECK = 1 << 2;
    public static final int FEATURE_SPLIT_TUNNEL = 1 << 3;
    public static final int FEATURE_KILL_SWITCH = 1 << 4;
    public static final int FEATURE_REWARD = 1 << 5;
    public static final int FEATURE_NODE_SWITCH = 1 << 6;

    private static final Object LOCK = new Object();
    private static boolean flushing;

    private UsageReport() {
    }

    // ------------------------------------------------------------ 记录

    /**
     * 记一次功能使用。写的是当天那一位，用于回答"做了这个功能到底有没有人用"。
     * 可以高频调用（只会置位并写一两个字节的偏好），不触发上报。
     */
    public static void feature(@NonNull Context context, int bit) {
        if (bit == 0) {
            return;
        }
        Context app = context.getApplicationContext();
        String key = KEY_MASK + dayKey(System.currentTimeMillis());
        SharedPreferences sp = prefs(app);
        int mask = sp.getInt(key, 0);
        if ((mask & bit) != 0) {
            return;
        }
        sp.edit().putInt(key, mask | bit).apply();
        pruneMask(app);
    }

    // ------------------------------------------------------------ 上报

    /**
     * 连接状态变化、App 启动时调用；做过节流，可以高频调用。
     * <p>
     * 节流看的是"上次尝试"而不是"上次成功"：服务端还没部署这个接口时，每次成功都不可能发生，
     * 若只按成功时间节流，用户每断开一次连接就打一次接口，白耗流量和电量。
     */
    public static void requestFlush(@NonNull Context context) {
        final Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        SharedPreferences sp = prefs(app);
        long lastTry = sp.getLong(KEY_LAST_TRY, 0);
        if (now - lastTry < MIN_INTERVAL_MS) {
            return;
        }
        sp.edit().putLong(KEY_LAST_TRY, now).apply();
        WORKER.execute(() -> flush(app));
    }

    /**
     * 立即上报最近 7 天。**只有服务端确认成功才更新"上次上报时间"**，
     * 失败就留到下次——离线用户的上报不能丢，否则留存曲线会凭空缺几天。
     */
    static void flush(@NonNull Context app) {
        final List<Item> batch;
        synchronized (LOCK) {
            if (flushing) {
                return;
            }
            batch = build(app);
            if (batch.isEmpty()) {
                return;
            }
            flushing = true;
        }
        prefs(app).edit().putString(KEY_INFLIGHT, GsonUtils.getInstance().toJson(batch)).apply();

        UsageDailyForm form = new UsageDailyForm();
        form.log = GsonUtils.getInstance().toJson(batch);
        BaseService service = new BaseService();
        service.setup(app);
        service.postData(Constants.getUrl(Constants.API_USAGE_URL), form,
                new CommonResponse.ResponseOkListener<NullReturnVo>() {
                    @Override
                    public void onResponse(NullReturnVo vo) {
                        synchronized (LOCK) {
                            flushing = false;
                        }
                        // 只推进时间戳，不记录"已发过哪几天"：补传是幂等的，少一次状态就少一处出错
                        prefs(app).edit()
                                .putLong(KEY_LAST_AT, System.currentTimeMillis())
                                .remove(KEY_INFLIGHT)
                                .apply();
                        LogUtil.i("使用数据上报成功 " + batch.size() + " 天");
                    }
                },
                new CommonResponse.ResponseErrorListener() {
                    @Override
                    public void onErrorResponse(com.android.volley.VolleyError e) {
                        synchronized (LOCK) {
                            flushing = false;
                        }
                        // 只写一句"失败"等于什么都没说：404（服务端还没部署这个接口）和
                        // 10000（写库失败）处理方式完全不同，所以把状态码和 errno 一并打出来。
                        // 数据本身每次都是从数据库重算的，不会丢，下次继续重试。
                        // 服务端返回非 0 errno 时抛的是 MyVolleyError：它没有调用 super(msg)，
                        // getMessage() 永远是 null，真正的文案在 getMsg() 里。只看 getMessage()
                        // 会把"写库失败"和"连不上服务器"混成同一句 "null"。
                        com.android.volley.NetworkResponse nr = e == null ? null : e.networkResponse;
                        int status = nr == null ? -1 : nr.statusCode;
                        String detail;
                        if (e instanceof com.openapi.common.exce.MyVolleyError) {
                            com.openapi.common.exce.MyVolleyError mve =
                                    (com.openapi.common.exce.MyVolleyError) e;
                            detail = "errno=" + mve.getErrno() + " msg=" + mve.getMsg();
                            // 服务端返回非 0 errno 时，msg 可能为空——那说明 errno 不是走
                            // 全局异常处理器产生的（那个会带上 "System error" 文案）。
                            // 把原始响应体打出来，否则只能看到一句 null，无从判断是哪类失败。
                            if (nr != null && nr.data != null) {
                                try {
                                    detail += " raw=" + new String(nr.data,
                                            java.nio.charset.StandardCharsets.UTF_8);
                                } catch (RuntimeException ignored) {
                                }
                            }
                        } else if (e instanceof com.android.volley.TimeoutError) {
                            detail = "timeout";
                        } else if (e instanceof com.android.volley.ParseError) {
                            detail = "parse";
                        } else if (e instanceof com.android.volley.NoConnectionError) {
                            detail = "no_connection";
                        } else {
                            detail = e == null ? "null_error" : e.getClass().getSimpleName()
                                    + ":" + e.getMessage();
                        }
                        LogUtil.i("使用数据上报失败，下次重试 status=" + status + " " + detail);
                    }
                }, "usage", NullReturnVo.class);
    }

    // ------------------------------------------------------------ 组装

    /** 把最近 {@link #DAYS} 天里有内容的天组装成上报项。查库，必须在后台线程调用。 */
    @NonNull
    static List<Item> build(@NonNull Context app) {
        List<Item> out = new ArrayList<>();
        UsageStats.Day[] days;
        try {
            days = SessionRecorder.loadDays(DAYS);
        } catch (RuntimeException e) {
            return out;
        }
        TimeZone tz = TimeZone.getDefault();
        for (UsageStats.Day d : days) {
            // 完全没用过的天不必占一行：留存看的是"有没有行"，空行会把安装日往前推，把 D1 算错
            if (d.sessions == 0 && d.connectedMs == 0) {
                continue;
            }
            Item item = new Item();
            item.day = dayKey(d.dayStart);
            item.tzOffsetMin = tz.getOffset(d.dayStart) / 60_000;
            item.connectedMs = d.connectedMs;
            item.sessions = d.sessions;
            item.rxBytes = d.rxBytes;
            item.txBytes = d.txBytes;
            item.nodes = d.nodes.size();
            item.protocols = join(d.protocols);
            item.featureMask = prefs(app).getInt(KEY_MASK + item.day, 0);
            out.add(item);
        }
        return out;
    }

    private static String join(java.util.Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(s);
        }
        String s = sb.toString();
        return s.length() > 128 ? s.substring(0, 128) : s;
    }

    /** 本地日期 yyyy-MM-dd。按设备时区算，与服务端 DATE 列对齐。 */
    @NonNull
    static String dayKey(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** 功能位只需要最近 7 天，其余清掉，免得偏好文件越积越大。 */
    private static void pruneMask(Context app) {
        SharedPreferences sp = prefs(app);
        String keep = dayKey(System.currentTimeMillis() - (DAYS - 1) * 86_400_000L);
        for (String k : sp.getAll().keySet()) {
            if (!k.startsWith(KEY_MASK)) {
                continue;
            }
            String day = k.substring(KEY_MASK.length());
            if (day.compareTo(keep) < 0) {
                sp.edit().remove(k).apply();
            }
        }
    }

    private static SharedPreferences prefs(Context app) {
        return app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static final java.util.concurrent.ExecutorService WORKER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "usage-report");
                t.setDaemon(true);
                return t;
            });

    /** 与服务端 UsageDailyPo 的字段名一致。 */
    static final class Item {
        String day;
        int tzOffsetMin;
        long connectedMs;
        int sessions;
        long rxBytes;
        long txBytes;
        int nodes;
        String protocols;
        int featureMask;
    }

    /** 测试与排障用：最近一次组装出来的结果有几行。 */
    @Nullable
    static String pendingDays(Context app) {
        return prefs(app).getString(KEY_INFLIGHT, null);
    }
}
