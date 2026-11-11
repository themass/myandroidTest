package com.openapi.ks.myapp.ui.sound;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.openapi.common.util.LogUtil;

/**
 * 声音列表页和声音详情页与 {@link PlayService} 的绑定。两边共用这一份连接逻辑，
 * 差别只在两点，用参数表示：
 * <ul>
 *     <li>断开时是否丢掉 service 引用（详情页丢弃，列表页保留，与原来各自的行为一致）；</li>
 *     <li>连上后刷新界面的内容，由 {@link Listener} 决定。</li>
 * </ul>
 */
public final class MusicServiceConnection implements ServiceConnection {

    public interface Listener {
        void onServiceReady(PlayService service);
    }

    private final Listener listener;
    private final boolean dropServiceOnDisconnect;
    private PlayService service;
    private boolean bound;

    public MusicServiceConnection(Listener listener, boolean dropServiceOnDisconnect) {
        this.listener = listener;
        this.dropServiceOnDisconnect = dropServiceOnDisconnect;
    }

    /** 绑定服务（BIND_AUTO_CREATE）。 */
    public void bind(Context context) {
        Intent intent = new Intent(context, PlayService.class);
        context.bindService(intent, this, Context.BIND_AUTO_CREATE);
        LogUtil.i("start bind music service");
    }

    /** 已绑定时解绑，否则不做任何事。 */
    public void unbind(Context context) {
        if (bound) {
            context.unbindService(this);
            bound = false;
        }
    }

    /** 当前服务；未连接过时为 null。 */
    public PlayService service() {
        return service;
    }

    public boolean isBound() {
        return bound;
    }

    /** 已绑定时返回服务，否则 null。 */
    public PlayService boundService() {
        return bound ? service : null;
    }

    @Override
    public void onServiceConnected(ComponentName className, IBinder binder) {
        service = ((PlayService.LocalBinder) binder).getService();
        LogUtil.i("bind music mService");
        bound = true;
        listener.onServiceReady(service);
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        bound = false;
        if (dropServiceOnDisconnect) {
            service = null;
        }
    }
}
