package com.openapi.data.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.openapi.common.util.PreferenceUtils;
import com.openapi.domain.model.TunnelProtocol;
import com.openapi.domain.model.VpnSessionState;
import com.openapi.ks.free1.R;
import com.openapi.ks.ui.main.MainFragmentViewPage;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.LocationUtil;

/**
 * 通知栏的唯一来源。
 * <p>
 * 之前通知由两套代码各自发：IKEv2 的 {@code CharonVpnService} 和 sing-box 的
 * {@code SingBoxVpnService}，各自读各自的状态，还有一处在点击瞬间提前改成"连接中"，
 * 所以通知和雷达经常对不上。现在：
 * <ul>
 *   <li>状态只来自 {@code CharonVpnSessionRepository} 发布的 {@link VpnSessionState}，
 *       雷达和通知读的是同一个值；</li>
 *   <li>通知文案和雷达按钮文案用同一套字符串，判定顺序也一致（先看错误，再看状态）；</li>
 *   <li>全进程只用一个通知 ID（{@link #NOTIFY_ID}），两个服务都不再自己拼通知。</li>
 * </ul>
 */
public final class VpnNotifier {

    /** 全进程唯一的 VPN 通知 ID。 */
    public static final int NOTIFY_ID = 100;
    /** 新的渠道 ID（旧的渠道 importance 不同，换 ID 避免沿用旧设置）。 */
    private static final String CHANNEL_ID = "VPN_STATUS_CHANNEL_V3";

    private static volatile Context sApp;
    private static volatile VpnSessionState sLast = VpnSessionState.IDLE;
    private static volatile TunnelProtocol sLastProtocol;

    private VpnNotifier() {
    }

    /** 启动时调用一次：记下应用 Context 并创建渠道。 */
    public static void init(Context context) {
        sApp = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = sApp.getSystemService(NotificationManager.class);
            if (nm != null) {
                // DEFAULT 而不是 LOW：LOW 会被系统收进"更多通知"，只显示精简文字，看不到带按钮的布局。
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "ShieldWing", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("VPN 连接状态");
                ch.enableLights(false);
                ch.enableVibration(false);
                // 测试：有声音，看 MIUI 是否把它放回主列表（静音的 DEFAULT 通知会被折叠进"更多通知"）
                android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                ch.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, attrs);
                nm.createNotificationChannel(ch);
            }
        }
    }

    /**
     * 状态变化时由仓库调用（主线程）。这是通知内容唯一的更新入口。
     */
    public static void render(VpnSessionState state, @Nullable TunnelProtocol protocol) {
        sLast = state == null ? VpnSessionState.IDLE : state;
        sLastProtocol = protocol;
        Context c = sApp;
        if (c == null) {
            return;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        boolean active = isActive(sLast);
        boolean switchOn = PreferenceUtils.getPrefBoolean(c, Constants.NOTIFY_SWITCH, true);
        if (!active && !switchOn) {
            nm.cancel(NOTIFY_ID);
            return;
        }
        nm.notify(NOTIFY_ID, build(c, sLast));
    }

    /** 是否已连接（供返回键等行为判断，与通知/雷达同源）。 */
    public static boolean isConnected() {
        return sLast.isConnected();
    }

    /** 服务进入前台时用：返回与当前状态一致的通知，避免服务自己拼一份。 */
    public static Notification current(Context context) {
        return build(context.getApplicationContext(), sLast);
    }

    /** 与雷达一致：连接中/已连接/断开中/出错 都算"活动"，通知保持常驻。 */
    private static boolean isActive(VpnSessionState s) {
        return s.hasError() || s.isConnected() || s.isConnecting() || s.isDisconnecting();
    }

    /**
     * 通知内容：带一个开关按钮的自定义布局（vpn_status_remote_view）。
     * 按钮的图标、转圈、文字都由同一个 VpnSessionState 决定，点击走 VpnNotificationReceiver，
     * 效果与雷达按钮一致（已连接/连接中 → 断开；未连接/出错 → 连接）。
     */
    private static Notification build(Context c, VpnSessionState s) {
        // 文案与雷达按钮完全一致（VpnRadFragment.render 的判定顺序：错误 → 连接状态）
        int statusRes;
        boolean connected = false;
        boolean busy = false;
        if (s.hasError()) {
            statusRes = R.string.vpn_bg_error;
        } else if (s.isConnected()) {
            statusRes = R.string.vpn_bg_conned;
            connected = true;
        } else if (s.isConnecting() || s.isDisconnecting()) {
            statusRes = R.string.vpn_bg_connecting_short;
            busy = true;
        } else {
            statusRes = R.string.vpn_bg_click;
        }
        String status = c.getString(statusRes);
        String node = safeNodeName(c);

        Intent open = new Intent(c, MainFragmentViewPage.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentPi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent toggle = new Intent(c, VpnNotificationReceiver.class)
                .setAction(VpnNotificationReceiver.ACTION_TOGGLE);
        PendingIntent togglePi = PendingIntent.getBroadcast(c, 1, toggle,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.vpn_status_remote_view);
        rv.setTextViewText(R.id.tv_vpn_content, status);
        rv.setTextViewText(R.id.tv_vpn_time, node == null ? "" : node);
        rv.setImageViewResource(R.id.btn_vpn, connected ? R.drawable.vpn_on : R.drawable.vpn_off);
        rv.setViewVisibility(R.id.btn_vpn, busy ? View.GONE : View.VISIBLE);
        rv.setViewVisibility(R.id.rb_conning, busy ? View.VISIBLE : View.GONE);
        rv.setOnClickPendingIntent(R.id.btn_vpn, togglePi);
        rv.setOnClickPendingIntent(R.id.tv_vpn_time, contentPi);

        return new NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(connected ? R.drawable.vpn_on : R.drawable.vpn_off)
                .setContentIntent(contentPi)
                .setContent(rv)          // 折叠状态也显示自定义布局（带按钮），与原来一致
                // 常驻：与原来前台服务通知一致，断开时也留在主列表里，按钮随时可点
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
    }

    @Nullable
    private static String safeNodeName(Context c) {
        try {
            return LocationUtil.getSelectName(c);
        } catch (Exception e) {
            return null;
        }
    }
}
