package com.openapi.ks.myapp.ui.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.openapi.core.util.LogUtil;
import com.openapi.ks.myapp.service.PlayService;

/**
 * 声音列表页与 {@link PlayService} 的绑定（从 {@link SoundItemsMusicFragment} 拆出，行为不变）。
 * 服务连上后通过 {@link Listener} 通知页面刷新界面。
 */
final class MusicServiceConnection implements ServiceConnection {

    interface Listener {
        void onServiceReady(PlayService service);
    }

    private final Listener listener;
    private PlayService service;
    private boolean bound;

    MusicServiceConnection(Listener listener) {
        this.listener = listener;
    }

    void bind(Context context) {
        Intent intent = new Intent(context, PlayService.class);
        context.bindService(intent, this, Context.BIND_AUTO_CREATE);
        LogUtil.i("start bind music service");
    }

    /** 最近一次连上的服务；未连接过时为 null。断开后仍保留引用，与拆分前一致。 */
    PlayService service() {
        return service;
    }

    boolean isBound() {
        return bound;
    }

    /** 已绑定时返回服务，否则 null。 */
    PlayService boundService() {
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
    }
}
