package com.openapi.data.vpn;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import com.openapi.domain.model.VpnSessionState;
import com.openapi.ks.free1.R;
import com.openapi.ks.ui.main.MainFragmentViewPage;
import com.openapi.myapp.base.AppContainer;

/**
 * 快捷设置面板里的 VPN 开关。
 * <p>
 * 下拉通知栏点一下就连接/断开，不用解锁、找图标、等首页加载——VPN 的真实使用场景
 * 就是"连上就走"，这个入口比任何首页改版都更直接地减少一次操作的成本。
 * <p>
 * 行为与雷达按钮、通知栏按钮完全一致（三者共用同一个 {@link VpnSessionState}）：
 * <ul>
 *   <li>已连接 / 连接中 → 直接断开，不需要界面；</li>
 *   <li>未连接 → 需要选中的节点和 VPN 授权，交给首页执行（与通知栏同一路径）。</li>
 * </ul>
 */
public class VpnTileService extends TileService {

    /**
     * 状态监听。TileService 不是 LifecycleOwner，用 observeForever 并在
     * onStopListening 里摘掉——否则面板反复开关会累积观察者，每个都触发一次 UI 更新。
     */
    @Nullable
    private Observer<VpnSessionState> stateObserver;

    @Override
    public void onStartListening() {
        super.onStartListening();
        startObserving();
        render();
    }

    @Override
    public void onStopListening() {
        stopObserving();
        super.onStopListening();
    }

    @Override
    public void onDestroy() {
        stopObserving();
        super.onDestroy();
    }

    @Override
    public void onClick() {
        super.onClick();
        VpnSessionState state = currentState();
        if (state.isConnected() || state.isConnecting() || state.isDisconnecting()) {
            // 已连接/正在连接的按下即断开：这一步不需要界面，可以直接做
            AppContainer.vpnSession().disconnect();
            // 断开是异步的，先按"断开中"渲染，真实状态到达后 observer 会再刷一次
            setTileState(Tile.STATE_INACTIVE, getString(R.string.vpn_bg_disconnecting));
            return;
        }
        // 连接需要 VPN 授权（VpnService.prepare）和选中的节点，两者都只能在界面里完成。
        // 用 Activity 拉起首页并触发与通知栏按钮相同的连接逻辑，避免在这里重写一遍选路代码。
        openAppToConnect();
    }

    /** 拉起首页并请求一次连接（复用通知栏按钮的开关通道）。 */
    private void openAppToConnect() {
        Intent intent = new Intent(this, MainFragmentViewPage.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(VpnNotificationReceiver.EXTRA_TOGGLE_ON_OPEN, true);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14 起该方法的 Intent 重载已废弃：改用 PendingIntent，
                // 系统才能把它当作"用户主动从磁贴启动界面"，不受后台启动限制。
                PendingIntent pi = PendingIntent.getActivity(this, 2, intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                startActivityAndCollapse(pi);
            } else {
                startActivityAndCollapse(intent);
            }
        } catch (RuntimeException e) {
            // 部分 ROM 在锁屏下会拒绝启动界面；退回普通 startActivity 至少不会崩
            startActivity(intent);
        }
    }

    // ------------------------------------------------------------ 状态同步

    private VpnSessionState currentState() {
        try {
            return AppContainer.vpnSession().current();
        } catch (IllegalStateException e) {
            // AppContainer 未初始化（理论上不会：MyApplication.onCreate 先于任何组件）
            return VpnSessionState.IDLE;
        }
    }

    private void startObserving() {
        if (stateObserver != null) {
            return;
        }
        try {
            Observer<VpnSessionState> observer = state -> render();
            stateObserver = observer;
            AppContainer.vpnSession().observeState().observeForever(observer);
        } catch (IllegalStateException e) {
            stateObserver = null;
        }
    }

    private void stopObserving() {
        Observer<VpnSessionState> observer = stateObserver;
        if (observer == null) {
            return;
        }
        stateObserver = null;
        try {
            AppContainer.vpnSession().observeState().removeObserver(observer);
        } catch (IllegalStateException ignored) {
            // 容器已销毁：观察者随进程一起消失，不需要处理
        }
    }

    /**
     * 磁贴的显示模型：标签文字 + 是否处于"激活"外观。
     * <p>
     * 只是两组值的组合，抽出来是为了能脱离 Android 单测——磁贴最容易错的地方就是
     * 状态与外观对不上（比如连接中显示成未连接，用户以为没生效又点一次）。
     */
    public static final class Ui {
        public final int labelRes;
        public final boolean active;

        Ui(int labelRes, boolean active) {
            this.labelRes = labelRes;
            this.active = active;
        }
    }

    /**
     * 状态 → 外观。判定顺序必须与雷达按钮、通知栏一致：先看错误，再看连接状态。
     * <p>
     * 三处的顺序一旦分叉，同一个时刻磁贴、通知、首页会显示三种说法。
     */
    @NonNull
    public static Ui uiFor(boolean hasError, boolean connected, boolean connecting,
                           boolean disconnecting) {
        if (hasError) {
            return new Ui(R.string.vpn_bg_error, false);
        }
        if (connected) {
            return new Ui(R.string.vpn_bg_conned, true);
        }
        if (connecting) {
            // 连接中保持可点：用户正是想再点一下取消
            return new Ui(R.string.vpn_bg_connecting_short, true);
        }
        if (disconnecting) {
            return new Ui(R.string.vpn_bg_disconnecting, false);
        }
        return new Ui(R.string.app_name, false);
    }

    private void render() {
        VpnSessionState s = currentState();
        Ui ui = uiFor(s.hasError(), s.isConnected(), s.isConnecting(), s.isDisconnecting());
        setTileState(ui.active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE, getString(ui.labelRes));
    }

    private void setTileState(int state, @NonNull String label) {
        Tile tile = getQsTile();
        if (tile == null) {
            // 面板未展开时 getQsTile() 可能为 null，不是错误
            return;
        }
        try {
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_qs_vpn));
            tile.setState(state);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setLabel(label);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    tile.setSubtitle(null);
                }
            } else {
                // Android 9 及以下没有 setLabel，只能靠图标本身表达状态
                tile.setLabel(label);
            }
            tile.updateTile();
        } catch (RuntimeException e) {
            // 个别 ROM 的 Tile 实现在未展开时 updateTile 会抛 IllegalStateException；
            // 磁贴是辅助入口，不值得因此让进程崩溃。
        }
    }
}
