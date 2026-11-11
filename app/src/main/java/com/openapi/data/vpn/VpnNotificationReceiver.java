package com.openapi.data.vpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.openapi.common.util.EventBusUtil;
import com.openapi.domain.model.VpnSessionState;
import com.openapi.ks.ui.main.MainFragmentViewPage;
import com.openapi.myapp.base.AppContainer;

/**
 * 通知栏按钮的点击处理。按钮状态与雷达一致，点击效果也一致：
 * <ul>
 *   <li>已连接 / 连接中 → 直接断开（不需要界面，仓库负责断开当前协议）；</li>
 *   <li>未连接 / 出错 → 让首页执行与雷达按钮完全相同的连接逻辑（连接需要选中的节点）。</li>
 * </ul>
 */
public class VpnNotificationReceiver extends BroadcastReceiver {

    public static final String ACTION_TOGGLE = "com.openapi.ks.free1.vpn.NOTIFICATION_TOGGLE";
    public static final String EXTRA_TOGGLE_ON_OPEN = "vpn_toggle_on_open";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_TOGGLE.equals(intent.getAction())) {
            return;
        }
        VpnSessionState s = AppContainer.vpnSession().current();
        if (s.isConnected() || s.isConnecting()) {
            AppContainer.vpnSession().disconnect();
            return;
        }
        // 连接需要首页的节点选择与界面状态：拉起首页，并在首页上执行雷达按钮的逻辑。
        Intent open = new Intent(context, MainFragmentViewPage.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra(EXTRA_TOGGLE_ON_OPEN, true);
        context.startActivity(open);
    }

    /** 首页用：收到"打开即切换"的请求后，交给雷达执行连接。 */
    public static void requestToggle() {
        EventBusUtil.getEventBus().post(new VpnToggleRequestEvent());
    }
}
