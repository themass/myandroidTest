package com.openapi.data.vpn.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;

import com.openapi.common.util.GsonUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.myapp.bean.form.MtuLogForm;
import com.openapi.myapp.bean.vo.NullReturnVo;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.BaseService;
import com.openapi.yewu.net.request.CommonResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 隧道 MTU 探测结果的本地队列 + 上报（doc/MTU-DESIGN.md 3.7）。
 * <p>
 * 每次连接结束记一条：协议、节点、传输类型、运营商（仅蜂窝）、链路 MTU、实际隧道 MTU、探针结果、是否降级。
 * 队列存在 SharedPreferences 里，最多 100 条；上报成功后才清空，失败保留到下次。
 * 不含 IP、设备标识以外的任何个人信息；设备 id 与出口 IP 由服务端从请求头取。
 */
public final class MtuReport {

    private static final String PREF = "mtu_report";
    private static final String KEY = "queue";
    private static final int MAX = 100;
    private static final Object LOCK = new Object();

    private MtuReport() {
    }

    /** 记录一次探测结果并尝试上报。可在任意线程调用。 */
    public static void record(Context context, TunnelMtuPolicy.Proto proto, @Nullable String node,
                              int mtu, TunnelProbe.Result probe, boolean retried) {
        Context app = context.getApplicationContext();
        Item item = new Item();
        item.node = node;
        item.protocol = proto.name();
        item.netType = TunnelMtuPolicy.networkType(app);
        item.carrier = "cell".equals(item.netType) ? carrier(app) : null;
        item.linkMtu = TunnelMtuPolicy.linkMtu(app);
        item.mtu = mtu;
        item.probe = probe.name();
        item.retried = retried;
        synchronized (LOCK) {
            List<Item> q = load(app);
            q.add(item);
            while (q.size() > MAX) {
                q.remove(0);
            }
            save(app, q);
        }
        new Handler(Looper.getMainLooper()).post(() -> flush(app));
    }

    /** 上报队列；成功后只删除已上报的那部分，之后新加的不会被误删。 */
    public static void flush(Context app) {
        final List<Item> batch;
        synchronized (LOCK) {
            batch = load(app);
        }
        if (batch.isEmpty()) {
            return;
        }
        MtuLogForm form = new MtuLogForm();
        form.log = GsonUtils.getInstance().toJson(batch);
        BaseService service = new BaseService();
        service.setup(app);
        service.postData(Constants.getUrl(Constants.API_MTULOG_URL), form, null,
                new CommonResponse.ResponseErrorListener() {
                    @Override
                    protected void onError() {
                        LogUtil.i("MTU 上报失败，保留到下次");
                    }
                }, "mtulog", NullReturnVo.class);
        // 服务端的写库失败也会返回成功（只记日志），这里不重复发送，直接按已上报清理。
        synchronized (LOCK) {
            List<Item> now = load(app);
            now.subList(0, Math.min(batch.size(), now.size())).clear();
            save(app, now);
        }
    }

    // ---------------------------------------------------------------- internals

    private static String carrier(Context app) {
        try {
            TelephonyManager tm = app.getSystemService(TelephonyManager.class);
            String op = tm == null ? null : tm.getNetworkOperator();
            return op == null || op.isEmpty() ? null : op;   // MCC+MNC，不含号码
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<Item> load(Context app) {
        SharedPreferences sp = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String json = sp.getString(KEY, null);
        List<Item> out = new ArrayList<>();
        if (json == null) {
            return out;
        }
        try {
            Item[] arr = GsonUtils.getInstance().fromJson(json, Item[].class);
            if (arr != null) {
                for (Item i : arr) {
                    out.add(i);
                }
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    private static void save(Context app, List<Item> q) {
        app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY, GsonUtils.getInstance().toJson(q)).apply();
    }

    /** 与服务端 MtuLogForm 的 log 数组字段名一致。 */
    static final class Item {
        String node;
        String protocol;
        String netType;
        String carrier;
        int linkMtu;
        int mtu;
        String probe;
        boolean retried;
    }
}
