package com.openapi.myapp.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.openapi.common.util.LogUtil;
import com.openapi.domain.model.TunnelProtocol;
import com.openapi.domain.model.VpnSessionState;
import com.openapi.myapp.bean.form.ConnSession;
import com.openapi.myapp.bean.form.ConnSessionDao;
import com.openapi.myapp.ui.check.ExitIpStore;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 记录每次 VPN 连接的会话（开始、结束、节点、协议、隧道流量）。
 * <p>
 * 挂在 {@code VpnSessionRepository.observeState()} 上，不需要改各个状态切换点：
 * <ul>
 *   <li>进入已连接：插入一行，记下节点与协议，开始采样；</li>
 *   <li>连接中每 30 秒采样一次，更新流量与结束时间，进程被杀时不会丢失太多；</li>
 *   <li>离开连接（空闲或出错）：最后采样一次并收尾。</li>
 * </ul>
 * 数据库操作全部放在单线程里执行，保证 insert 先于 update。
 */
public final class SessionRecorder implements Observer<VpnSessionState> {

    private static final long SAMPLE_INTERVAL_MS = 30_000L;

    /** 进程内唯一实例，由 AppContainer 创建。界面通过它读取当前会话。 */
    @Nullable
    private static volatile SessionRecorder instance;

    @Nullable
    public static SessionRecorder get() {
        return instance;
    }

    private final Context appContext;
    private final LiveData<TunnelProtocol> protocol;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** insert 之后写入，供后续 update 使用；0 表示尚未插入。 */
    private final AtomicLong rowId = new AtomicLong(0);

    @Nullable
    private ConnSession current;
    private long lastRx;
    private long lastTx;

    private final Runnable sampler = new Runnable() {
        @Override
        public void run() {
            sample();
            main.postDelayed(this, SAMPLE_INTERVAL_MS);
        }
    };

    public SessionRecorder(@NonNull Context context, @NonNull LiveData<TunnelProtocol> protocol) {
        this.appContext = context.getApplicationContext();
        this.protocol = protocol;
        instance = this;
        io.execute(this::closeOrphans);
    }

    @Override
    public void onChanged(VpnSessionState state) {
        boolean live = state.isConnected() || state.isConnecting();
        if (current == null) {
            if (state.isConnected()) {
                start();
            }
        } else if (!live) {
            finish();
        }
    }

    private void start() {
        long[] raw = TunnelTraffic.read();
        lastRx = raw == null ? 0 : raw[0];
        lastTx = raw == null ? 0 : raw[1];

        long now = System.currentTimeMillis();
        TunnelProtocol p = protocol.getValue();
        String protocolName = p == null ? "UNKNOWN" : p.name();
        ConnSession session = new ConnSession(null, now, now,
                ExitIpStore.getNodeGateway(appContext), protocolName, 0, 0, true);
        current = session;

        final ConnSession row = copy(session);
        io.execute(() -> {
            try {
                dao().insert(row);
                rowId.set(row.id);
            } catch (RuntimeException e) {
                LogUtil.e("会话记录插入失败: " + e.getMessage());
            }
        });
        main.removeCallbacks(sampler);
        main.postDelayed(sampler, SAMPLE_INTERVAL_MS);
    }

    /** 把当前累计值与结束时间写回数据库。 */
    private void sample() {
        ConnSession session = current;
        if (session == null) {
            return;
        }
        long[] raw = TunnelTraffic.read();
        if (raw != null) {
            // 网卡重建后计数从 0 开始，此时 raw 本身就是新增量
            session.rxBytes += raw[0] >= lastRx ? raw[0] - lastRx : raw[0];
            session.txBytes += raw[1] >= lastTx ? raw[1] - lastTx : raw[1];
            lastRx = raw[0];
            lastTx = raw[1];
        }
        session.endAt = System.currentTimeMillis();
        update(copy(session));
    }

    private void finish() {
        main.removeCallbacks(sampler);
        sample();
        ConnSession session = current;
        if (session != null) {
            session.open = false;
            update(copy(session));
        }
        current = null;
    }

    private void update(@NonNull ConnSession row) {
        io.execute(() -> {
            long id = rowId.get();
            if (id == 0) {
                return;
            }
            try {
                row.id = id;
                dao().update(row);
            } catch (RuntimeException e) {
                LogUtil.e("会话记录更新失败: " + e.getMessage());
            }
        });
    }

    // ---------------------------------------------------------------- 查询（给界面用）

    /** 当前会话的开始时间（毫秒）；没有连接时为 0。主线程调用。 */
    public long currentStartAt() {
        ConnSession s = current;
        return s == null ? 0 : s.startAt;
    }

    /**
     * 当前会话到现在的累计流量 {rx, tx}。读的是实时计数，不等 30 秒一次的采样，
     * 所以首页卡片每秒刷新时数字是连续的。主线程调用，不碰数据库。
     */
    @NonNull
    public long[] currentTraffic() {
        ConnSession s = current;
        if (s == null) {
            return new long[]{0, 0};
        }
        long rx = s.rxBytes;
        long tx = s.txBytes;
        long[] raw = TunnelTraffic.read();
        if (raw != null) {
            rx += raw[0] >= lastRx ? raw[0] - lastRx : raw[0];
            tx += raw[1] >= lastTx ? raw[1] - lastTx : raw[1];
        }
        return new long[]{rx, tx};
    }

    /** 读最近 {@code days} 天的汇总。查数据库，必须在后台线程调用。 */
    @NonNull
    public static UsageStats.Day[] loadDays(int days) {
        long now = System.currentTimeMillis();
        long since = UsageStats.startOfDay(now, java.util.TimeZone.getDefault()) - (long) days * 86_400_000L;
        List<UsageStats.Span> spans = new java.util.ArrayList<>();
        try {
            // 进行中的会话 endAt 可能还没刷新，用 max(endAt, now) 兜底由调用方处理；这里按库里的值取
            for (ConnSession c : dao().queryBuilder()
                    .where(ConnSessionDao.Properties.StartAt.ge(since)).list()) {
                long end = c.open ? Math.max(c.endAt, now) : c.endAt;
                spans.add(new UsageStats.Span(c.startAt, end, c.rxBytes, c.txBytes));
            }
        } catch (RuntimeException e) {
            LogUtil.e("读取会话统计失败: " + e.getMessage());
        }
        return UsageStats.lastDays(spans, now, days, java.util.TimeZone.getDefault());
    }

    /** 进程上次被杀时仍处于 open 的会话，在这次启动时收尾。 */
    private void closeOrphans() {
        try {
            List<ConnSession> orphans = dao().queryBuilder()
                    .where(ConnSessionDao.Properties.Open.eq(true))
                    .list();
            for (ConnSession s : orphans) {
                s.open = false;
                dao().update(s);
            }
        } catch (RuntimeException e) {
            LogUtil.e("会话记录收尾失败: " + e.getMessage());
        }
    }

    private static ConnSessionDao dao() {
        return com.openapi.myapp.data.DBManager.getInstance().getDaoSession().getConnSessionDao();
    }

    private static ConnSession copy(ConnSession s) {
        return new ConnSession(s.id, s.startAt, s.endAt, s.node, s.protocol, s.rxBytes, s.txBytes, s.open);
    }
}
