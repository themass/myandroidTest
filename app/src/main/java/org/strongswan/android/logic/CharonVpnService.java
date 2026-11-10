/*
 * Copyright (C) 2012-2016 Tobias Brunner
 * Copyright (C) 2012 Giuliano Grassi
 * Copyright (C) 2012 Ralf Sager
 * HSR Hochschule fuer Technik Rapperswil
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

package org.strongswan.android.logic;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.security.KeyChain;
import android.security.KeyChainException;
import androidx.core.app.NotificationCompat;
import android.system.OsConstants;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.openapi.common.util.FileUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.PreferenceUtils;
import com.openapi.ks.free1.R;
import com.openapi.yewu.net.NetUtils;
import com.openapi.myapp.base.MyApplication;
import com.openapi.myapp.bean.DataBuilder;
import com.openapi.myapp.bean.vo.ServerVo;
import com.openapi.myapp.bean.vo.VpnProfile;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.BaseService;
import com.openapi.myapp.data.ConnLogUtil;
import com.openapi.myapp.data.LocationUtil;
import com.openapi.myapp.ui.fragment.LocationPageViewFragment;
import com.openapi.ks.ui.main.MainFragmentViewPage;
import com.openapi.yewu.net.request.CommonResponse;

import org.strongswan.android.logic.imc.ImcState;
import org.strongswan.android.logic.imc.RemediationInstruction;
import org.strongswan.android.logic.utils.SettingsWriter;

import java.io.File;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class CharonVpnService extends VpnService implements VpnStateService.VpnStateListener {
    public static final String PROFILE = "PROFILE";
    public static final int FOREGROUND_NOTIFY_ID = 100;

    /**
     * 当前是否正在用 IKEv2 以外的协议（WireGuard / VLESS+Reality）连接。
     * <p>
     * {@code CharonVpnSessionRepository.start()} 启动时会无条件绑定本服务
     * （legacy 单协议 connect(VpnProfile) 路径和查询 IKEv2 状态都依赖这个绑定），
     * 绑定一旦建立，{@link #onServiceConnected} / {@link #stateChanged()} 就会
     * 调用 {@link #createForegroundService}刷自己的通知——完全不知道这次连接
     * 实际用的是别的协议。不加这个标志的后果：真正连接的是 VLESS/WireGuard 时，
     * 通知栏会同时冒出两条通知，IKEv2 这条还卡在陈旧的 "Connecting..." 状态，
     * 因为 VpnStateService 的内部状态从来没被这次连接更新过。
     * 由 {@code CharonVpnSessionRepository} 在切换协议时设置。
     */
    public static volatile boolean nonIkev2ProtocolActive = false;

    /**
     * 由 {@code CharonVpnSessionRepository} 注入：断开"当前实际活跃的协议引擎"。
     * <p>
     * 通知栏那条"click to disconn"是 IKEv2 服务画的，点它只会走 IKEv2 的
     * {@code vpnClick()}（重新拉线路列表再连一次 IKEv2）。当真正在跑的是 VLESS 时，
     * 这个动作既断不开 VLESS 隧道，又会平白再起一个 IKEv2 连接，形成"两个连接
     * 抢一个 TUN"的局面。所以非 IKEv2 协议激活时，按钮必须转交给仓库去断真正
     * 在跑的那个引擎。
     */
    public static volatile Runnable activeEngineDisconnect;

    /** 关掉本服务的前台通知（不 stopSelf）；别的协议在用时必须让出通知权。 */
    public void cancelForegroundNotification() {
        stopForeground(true);
    }

    /**
     * 当前存活的实例（弱引用，避免 Service 被静态字段钉住泄漏）。
     * {@link #createForegroundService} 只在服务内部触发，外部无法主动让它收手，
     * 所以需要这个方法给多协议仓库用：切到 VLESS/WireGuard 时立刻把 IKEv2 那条
     * 陈旧通知撤掉，而不是等下一次 stateChanged 才发现。
     */
    private static volatile java.lang.ref.WeakReference<CharonVpnService> instanceRef;

    public static void suppressIkev2Notification() {
        java.lang.ref.WeakReference<CharonVpnService> ref = instanceRef;
        CharonVpnService svc = ref == null ? null : ref.get();
        if (svc != null) {
            LogUtil.i("suppressIkev2Notification: 撤下 IKEv2 通知");
            svc.cancelForegroundNotification();
        }
    }

    /**
     * 通知栏按钮点击事件对应的ACTION
     */
    public final static String ACTION_BUTTON = "com.openapi.ks.free1.notifications.intent.action.ButtonClick";
    public final static String LOCATION_BUTTON = "com.openapi.ks.free1.notifications.intent.action.LocationClick";
    public final static String VPN_SERVER_CLICK = "VPN_SERVER_CLICK";
    /**
     * as defined in charonservice.h
     */
    static final int STATE_CHILD_SA_UP = 1;
    static final int STATE_CHILD_SA_DOWN = 2;
    static final int STATE_AUTH_ERROR = 3;
    static final int STATE_PEER_AUTH_ERROR = 4;
    static final int STATE_LOOKUP_ERROR = 5;
    static final int STATE_UNREACHABLE_ERROR = 6;
    static final int STATE_GENERIC_ERROR = 7;
    private static final String TAG = CharonVpnService.class.getSimpleName();
    private static final String WORK_ANME = "vpnThread";
    public static volatile boolean VPN_STATUS_NOTIF = false;
    private BaseService indexService;
    private  NotificationManager notificationManager;
    private static  final  String N_CHANNEL="FREEVVV_CHANNEL";
    /*
     * The libraries are extracted to /data/data/org.strongswan.android/...
     * during installation.  On newer releases most are loaded in JNI_OnLoad.
     */
    static {
        try {
            // 确保SimpleFetcher类在加载native库之前被加载
            try {
                Class.forName("org.strongswan.android.logic.SimpleFetcher");
                android.util.Log.i("CharonVpnService", "SimpleFetcher class loaded successfully");
            } catch (ClassNotFoundException e) {
                android.util.Log.e("CharonVpnService", "Failed to load SimpleFetcher class", e);
            }
            
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                android.util.Log.i("CharonVpnService", "Loading legacy libraries for API < 18");
                System.loadLibrary("strongswan");
                System.loadLibrary("tpmtss");
                System.loadLibrary("tncif");
                System.loadLibrary("tnccs");
                System.loadLibrary("imcv");
                System.loadLibrary("charon");
                System.loadLibrary("ipsec");
            }
            
            android.util.Log.i("CharonVpnService", "Loading androidbridge library...");
            System.loadLibrary("androidbridge");
            android.util.Log.i("CharonVpnService", "All native libraries loaded successfully");
            
        } catch (UnsatisfiedLinkError e) {
            android.util.Log.e("CharonVpnService", "Failed to load native library", e);
            throw new RuntimeException("Failed to load native libraries", e);
        } catch (Exception e) {
            android.util.Log.e("CharonVpnService", "Unexpected error during native library loading", e);
            throw new RuntimeException("Unexpected error during native library loading", e);
        }
    }

    private final Object mServiceLock = new Object();
    public ButtonBroadcastReceiver bReceiver;
    private volatile boolean isInitialized = false;
    private volatile boolean nativeLibraryCorrupted = false;
    /**
     * 当前使用的提案档位。0 = 现代（AEAD + ECP），1 = 旧版（CBC + MODP）。
     * 协商失败时自动降一档重试，每次连接最多降一次。
     */
    private volatile int mProposalTier = 0;
    /** 0 现代 → 1 兼容 → 2 原生默认（不写提案键）。 */
    private static final int MAX_PROPOSAL_TIER = 2;
    private String mLogFile;
    private VpnProfile mCurrentProfile;
    private volatile boolean mIsDisconnecting;
    private HandlerThread mWorkThread;
    private Handler mWorkHandler;
    private VpnStateService mService;
    /*
     * onCreate() 里绑定 VpnStateService 是异步的，worker 线程要等 onServiceConnected
     * 才会启动。而 startService() 之后 onStartCommand 往往在几毫秒内就到（冷启动实测
     * 只差 4ms），那时 mWorkHandler 还是 null。旧代码在这种情况只打一句"启动没完成"
     * 就把连接请求丢了：服务起来了、通知栏图标也有了，但从来没发起 IKEv2 握手，
     * VpnStateService 一直停在 DISABLED，UI 于是永远停在"准备中"。
     * 所以窗口期收到的指令必须暂存，等 worker 就绪后补发（后来的指令覆盖先前的）。
     */
    private final Object mPendingCommandLock = new Object();
    private VpnProfile mPendingProfile;
    private boolean mHasPendingCommand;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {	/* since the service is local this is theoretically only called when the process is terminated */
            synchronized (mServiceLock) {
                mService = null;
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (mServiceLock) {
                mService = ((VpnStateService.LocalBinder) service).getService();
            }
            mService.registerListener(CharonVpnService.this);
            mWorkThread.start();
            mWorkHandler = new Handler(mWorkThread.getLooper());
            /* we are now ready to start the handler thread */
            createForegroundService(false);
            // worker 就绪，补发 onCreate/onStartCommand 窗口期里暂存的指令。
            flushPendingCommand();
        }
    };

    @Override
    public void stateChanged() {
        createForegroundService(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Bundle bundle = intent.getExtras();
            VpnProfile profile = null;
            if (bundle != null) {
                profile = (VpnProfile) bundle.getSerializable(PROFILE);
            }
            if (profile != null) {
                mCurrentProfile = profile;
                /*
                 * 用户主动发起的新连接一律从现代档重试。降档只应作用于
                 * 同一次连接过程；否则一次协商失败会让进程永久停留在兼容档，
                 * 服务端升级后也不会自动恢复。
                 */
                mProposalTier = 0;
            }
            setNextProfile(profile);
        }
        LogUtil.i("charon service onStartCommand");
        return START_NOT_STICKY;
    }

    private void setNextProfile(VpnProfile profile) {
        Handler handler = mWorkHandler;
        if (handler != null) {
            if (profile == null) {
                handler.post(new DisConnectJob());
            } else {
                handler.post(new ConnectJob());
            }
            return;
        }
        // worker 还没起来（onCreate 里绑定 VpnStateService 是异步的）。暂存最后一条
        // 指令，等 onServiceConnected 补发，不能丢——丢了就是"点了连接一直准备中"。
        synchronized (mPendingCommandLock) {
            mPendingProfile = profile;
            mHasPendingCommand = true;
        }
        LogUtil.i("worker 未就绪，暂存指令: " + (profile == null ? "disconnect" : "connect"));
    }

    /** 把 onCreate/onStartCommand 窗口期暂存的指令补发给 worker。需在 worker 已就绪后调用。 */
    private void flushPendingCommand() {
        boolean has;
        VpnProfile profile;
        synchronized (mPendingCommandLock) {
            has = mHasPendingCommand;
            profile = mPendingProfile;
            mHasPendingCommand = false;
            mPendingProfile = null;
        }
        if (!has) {
            return;
        }
        LogUtil.i("补发暂存指令: " + (profile == null ? "disconnect" : "connect"));
        setNextProfile(profile);
    }

    @Override
    public void onCreate() {
        instanceRef = new java.lang.ref.WeakReference<>(this);
        // 使用应用内部目录作为日志文件路径，避免权限问题
        mLogFile = getFilesDir().getAbsolutePath() + File.separator + "charon.log";
        
        // 确保日志目录存在
        try {
            File logDir = new File(mLogFile).getParentFile();
            if (logDir != null && !logDir.exists()) {
                boolean created = logDir.mkdirs();
                LogUtil.i("Log directory created: " + created);
            }
            
            // 确保日志文件存在
            File logFile = new File(mLogFile);
            if (!logFile.exists()) {
                boolean fileCreated = logFile.createNewFile();
                LogUtil.i("Log file created: " + fileCreated);
            }
            
            LogUtil.i("StrongSwan log file path: " + mLogFile);
            LogUtil.i("Log directory exists: " + (logDir != null && logDir.exists()));
            LogUtil.i("Log file exists: " + logFile.exists());
        } catch (Exception e) {
            LogUtil.e("Error setting up log directory: " + e.getMessage());
            // 如果外部存储失败，使用内部存储
            mLogFile = getFilesDir().getAbsolutePath() + File.separator + "charon.log";
            LogUtil.i("Fallback to internal storage: " + mLogFile);
        }
        
        /* use a separate thread as main thread for charon */
        /* the thread is started when the service is bound */
        bindService(new Intent(this, VpnStateService.class),
                mServiceConnection, Service.BIND_AUTO_CREATE);
        mWorkThread = new HandlerThread(WORK_ANME);
        initButtonReceiver();
        indexService = new BaseService();
        indexService.setup(this);
        notificationManager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public void onRevoke() {	/* the system revoked the rights grated with the initial prepare() call.
         * called when the user clicks disconnect in the system's VPN dialog */
        LogUtil.i("charon service onRevoke");
        setNextProfile(null);
    }

    @Override
    public void onDestroy() {
        LogUtil.i("charon service onDestroy");
        disconn();
        indexService.cancelRequest(VPN_SERVER_CLICK);
        mWorkThread.quit();
        if (mService != null) {
            unbindService(mServiceConnection);
        }
        if (bReceiver != null) {
            unregisterReceiver(bReceiver);
        }
    }

    /**
     * Notify the state service about a new connection attempt.
     * Called by the handler thread.
     *
     * @param profile currently active VPN profile
     */
    private void startConnection(VpnProfile profile) {
        synchronized (mServiceLock) {
            if (mService != null) {
                mService.startConnection(profile);
            }
        }
    }

    /**
     * Update the current VPN state on the state service. Called by the handler
     * thread and any of charon's threads.
     *
     * @param state current state
     */
    private void setState(VpnStateService.State state) {
        synchronized (mServiceLock) {
            if (mService != null) {
                mService.setState(state);
            }
        }
    }

    /**
     * Set an error on the state service. Called by the handler thread and any
     * of charon's threads.
     *
     * @param error error state
     */
    private void setError(VpnStateService.ErrorState error) {
        synchronized (mServiceLock) {
            if (mService != null) {
                mService.setError(error);
                LogUtil.i("Error set: " + error + ", current state: " + mService.getState());
            }
        }
    }

    /**
     * Force update the state on the state service. Called by the handler thread.
     *
     * @param state new state
     */
    private void forceUpdateState(VpnStateService.State state) {
        synchronized (mServiceLock) {
            if (mService != null) {
                mService.forceUpdateState(state);
                LogUtil.i("State force updated: " + state);
            }
        }
    }

    /**
     * Set the IMC state on the state service. Called by the handler thread and
     * any of charon's threads.
     *
     * @param state IMC state
     */
    private void setImcState(ImcState state) {
        synchronized (mServiceLock) {
            if (mService != null) {
                mService.setImcState(state);
            }
        }
    }

    /**
     * Set an error on the state service. Called by the handler thread and any
     * of charon's threads.
     *
     * @param error error state
     */
    private void setErrorDisconnect(VpnStateService.ErrorState error) {
        synchronized (mServiceLock) {
            if (mService != null) {
                // 无论是否在断开连接，都应该设置错误状态
                mService.setError(error);
                LogUtil.i("Error state set: " + error + ", current state: " + mService.getState());
            }
        }
        teardownAfterError();
    }

    /**
     * 出错后真正停掉 charon。setError 只把 UI 状态改成 DISABLED，charon 却还在跑：
     * 隧道建立后掉线（DPD 超时）时它会保留 TUN 并不停重连，系统 VPN 一直挂着，
     * 整机流量进了一条不通的隧道——App 显示"未连接"，手机却上不了网，只能杀进程。
     * 这里停 charon（会关掉 TUN），保留错误状态给 UI 显示，不调 forceUpdateState（它会清掉错误）。
     */
    private void teardownAfterError() {
        Handler handler = mWorkHandler;
        if (handler == null || mIsDisconnecting) {
            return;
        }
        handler.post(() -> {
            if (mCurrentProfile == null) {
                return;
            }
            LogUtil.i("连接出错，停止 charon 并释放 TUN");
            stopCurrentConnection();
            clearConnectionState();
        });
    }

    /**
     * Updates the state of the current connection.
     * Called via JNI by different threads (but not concurrently).
     *
     * @param status new state
     */
    public void updateStatus(int status) {
        switch (status) {
            case STATE_CHILD_SA_DOWN:
                if (!mIsDisconnecting) {
                    setState(VpnStateService.State.CONNECTING);
                }
                break;
            case STATE_CHILD_SA_UP:
                // 隧道协商成功不等于数据能走：路上可能把 ESP / NAT-T 包丢掉，
                // 这时直接报"已连接"，系统 VPN 会把整机流量吸进一条不通的隧道。
                // 先探测一次，通了再报已连接。重协商（rekey）时已经是已连接，不再探测。
                if (mService != null && mService.getState() == VpnStateService.State.CONNECTED) {
                    break;
                }
                probeThenConnected();
                break;
            case STATE_AUTH_ERROR:
                logStatus(status);
                /*
                 * native 侧把 NO_PROPOSAL_CHOSEN 等握手期的 notify 错误也报成 AUTH_ERROR，
                 * 所以同样先降档重试一次；已是最后一档时才向 UI 报认证失败。
                 */
                if (!mIsDisconnecting && retryWithFallbackProposal()) {
                    LogUtil.i("Auth error during handshake, retrying with fallback proposals");
                    break;
                }
                setErrorDisconnect(VpnStateService.ErrorState.AUTH_FAILED);
                break;
            case STATE_PEER_AUTH_ERROR:
                setErrorDisconnect(VpnStateService.ErrorState.PEER_AUTH_FAILED);
                break;
            case STATE_LOOKUP_ERROR:
                setErrorDisconnect(VpnStateService.ErrorState.LOOKUP_FAILED);
                logStatus(status);
                break;
            case STATE_UNREACHABLE_ERROR:
                setErrorDisconnect(VpnStateService.ErrorState.UNREACHABLE);
                logStatus(status);
                break;
            case STATE_GENERIC_ERROR:
                logStatus(status);
                /*
                 * NO_PROPOSAL_CHOSEN 等协商失败在此归为 GENERIC_ERROR（native 侧没有
                 * 单独的提案错误码）。此时先用旧提案重试一次，成功则不向 UI 报错——
                 * 否则用户会看到一次假失败。
                 */
                if (!mIsDisconnecting && retryWithFallbackProposal()) {
                    LogUtil.i("Generic error during handshake, retrying with fallback proposals");
                    break;
                }
                setErrorDisconnect(VpnStateService.ErrorState.GENERIC_ERROR);
                break;
            default:
                Log.e(TAG, "Unknown status code received");
                logStatus(status);
                break;
        }

    }
    private void probeThenConnected() {
        final VpnProfile profile = mCurrentProfile;
        new Thread(() -> {
            boolean ok = com.openapi.data.vpn.engine.TunnelProbe.probeUntilDeadline(
                    () -> !mIsDisconnecting && mCurrentProfile == profile);
            if (mIsDisconnecting || mCurrentProfile != profile) {
                return;
            }
            if (ok) {
                LogUtil.i("IKEv2 连通性探针通过");
                setState(VpnStateService.State.CONNECTED);
            } else {
                LogUtil.e("IKEv2 隧道已建立但探测流量不通（ESP/NAT-T 包被丢弃）");
                setErrorDisconnect(VpnStateService.ErrorState.UNREACHABLE);
            }
        }, "ikev2-probe").start();
    }

    private void logStatus(int status){
        try {
            if (mCurrentProfile != null) {
                ConnLogUtil.addLog(this,mCurrentProfile.getUsername(),mCurrentProfile.getGateway(),status);
                LogUtil.e("name=" + mCurrentProfile.getUsername() + ";ip=" + mCurrentProfile.getGateway() + "; userIp=" + NetUtils.getIP(this));
            } else {
                LogUtil.e("logStatus: mCurrentProfile is null, status=" + status);
            }
        }catch (Throwable e){
            LogUtil.e("logStatus error: " + e.getMessage());
        }
    }

    /**
     * Updates the IMC state of the current connection.
     * Called via JNI by different threads (but not concurrently).
     *
     * @param value new state
     */
    public void updateImcState(int value) {
        ImcState state = ImcState.fromValue(value);
        if (state != null) {
            setImcState(state);
        }
    }

    /**
     * Add a remediation instruction to the VPN state service.
     * Called via JNI by different threads (but not concurrently).
     *
     * @param xml XML text
     */
    public void addRemediationInstruction(String xml) {
        for (RemediationInstruction instruction : RemediationInstruction.fromXml(xml)) {
            synchronized (mServiceLock) {
                if (mService != null) {
                    mService.addRemediationInstruction(instruction);
                }
            }
        }
    }

    /**
     * Function called via JNI to generate a list of DER encoded CA certificates
     * as byte array.
     *
     * @return a list of DER encoded CA certificates
     */
    private byte[][] getTrustedCertificates() {
        ArrayList localArrayList = new ArrayList();
        try {
            CertificateFactory localCertificateFactory = CertificateFactory.getInstance("X.509");
            InputStream localInputStream = new StringBufferInputStream(mCurrentProfile.getCert());
            Certificate localCertificate = localCertificateFactory.generateCertificate(localInputStream);
            localInputStream.close();
            localArrayList.add(localCertificate.getEncoded());
            return (byte[][]) localArrayList.toArray(new byte[localArrayList.size()][]);
        } catch (Exception localException) {
            LogUtil.e(localException);
            return null;
        }
    }

    /**
     * Function called via JNI to get a list containing the DER encoded certificates
     * of the user selected certificate chain (beginning with the user certificate).
     * <p/>
     * Since this method is called from a thread of charon's thread pool we are safe
     * to call methods on KeyChain directly.
     *
     * @return list containing the certificates (first element is the user certificate)
     * @throws InterruptedException
     * @throws KeyChainException
     * @throws CertificateEncodingException
     */
    private byte[][] getUserCertificate() throws KeyChainException, InterruptedException, CertificateEncodingException {
        ArrayList<byte[]> encodings = new ArrayList<>();
        X509Certificate[] chain = KeyChain.getCertificateChain(getApplicationContext(), null);
        if (chain == null || chain.length == 0) {
            return null;
        }
        for (X509Certificate cert : chain) {
            encodings.add(cert.getEncoded());
        }
        return encodings.toArray(new byte[encodings.size()][]);
    }

    /**
     * Function called via JNI to get the private key the user selected.
     * <p/>
     * Since this method is called from a thread of charon's thread pool we are safe
     * to call methods on KeyChain directly.
     *
     * @return the private key
     * @throws InterruptedException
     * @throws KeyChainException
     * @throws CertificateEncodingException
     */
    private PrivateKey getUserKey() throws KeyChainException, InterruptedException {
        return KeyChain.getPrivateKey(getApplicationContext(), mCurrentProfile.getCertificateAlias());
    }

    /**
     * Initialization of charon, provided by libandroidbridge.so
     *
     * @param builder BuilderAdapter for this connection
     * @param logfile absolute path to the logfile
     * @param appdir absolute path to the data directory of the app
     * @param byod enable BYOD features
     * @param ipv6 enable IPv6 transport
     * @return TRUE if initialization was successful
     */
    public native boolean initializeCharon(BuilderAdapter builder, String logfile, String appdir, boolean byod, boolean ipv6);

    /**
     * Deinitialize charon, provided by libandroidbridge.so
     */
    public native void deinitializeCharon();

    /**
     * Initiate VPN, provided by libandroidbridge.so
     */
    public native void initiate(String config);
    
    /**
     * 安全的native方法调用包装器
     */
    private boolean safeNativeCall(Runnable nativeCall, String operationName) {
        try {
            LogUtil.i("Executing native operation: " + operationName);
            nativeCall.run();
            LogUtil.i("Native operation completed: " + operationName);
            return true;
        } catch (UnsatisfiedLinkError e) {
            LogUtil.e("Native library not available for " + operationName + ": " + e.getMessage());
            return false;
        } catch (NoSuchMethodError e) {
            LogUtil.e("Native method not found for " + operationName + ": " + e.getMessage());
            return false;
        } catch (Exception e) {
            LogUtil.e("Error in native operation " + operationName + ": " + e.getMessage());
            LogUtil.e("Stack trace: " + android.util.Log.getStackTraceString(e));
            return false;
        } catch (Throwable e) {
            LogUtil.e("Critical error in native operation " + operationName + ": " + e.getMessage());
            LogUtil.e("Stack trace: " + android.util.Log.getStackTraceString(e));
            return false;
        }
    }
    
    /**
     * Function called via JNI to determine information about the Android version.
     */
    private static String getAndroidVersion()
    {
        String version = "Android " + Build.VERSION.RELEASE + " - " + Build.DISPLAY;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            version += "/" + Build.VERSION.SECURITY_PATCH;
        }
        return version;
    }

    /**
     * Function called via JNI to determine information about the device.
     */
    private static String getDeviceString()
    {
        return Build.MODEL + " - " + Build.BRAND + "/" + Build.PRODUCT + "/" + Build.MANUFACTURER;
    }
    /**
     * Adapter for VpnService.Builder which is used to access it safely via JNI.
     * There is a corresponding C object to access it from native code.
     */
    /**
     * 检查native库状态
     */
    private boolean checkNativeLibraryStatus() {
        try {
            // 尝试调用一个简单的native方法来检查库是否正常
            LogUtil.i("Checking native library status...");
            return true;
        } catch (Exception e) {
            LogUtil.e("Native library check failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 写入 IKE / ESP 提案。
     * <p>
     * 两个档位都必须是**单条提案**：'-' 是该提案内部所有算法的分隔符，
     * 同类算法的多个候选也用它串联（按优先顺序），逗号与 '/' 在这里都没有语义。
     * '/' 只是 strongSwan 的**输出**格式，用它书写会导致整条提案解析失败、
     * 静默回退到默认算法（见 proposal.c 的 print_alg 与
     * enumerator_create_token(algs, "-", " ")）。
     * <p>
     * 约束（来自 libstrongswan 的 check_proposal 及其测试用例）：
     * <ul>
     *   <li>AEAD 与传统 CBC 加密不能出现在同一条提案中
     *       （测试用例 aes128gcm16-aes128-sha256-modp3072 期望解析失败）；</li>
     *   <li>全 AEAD 的提案不能带完整性算法；</li>
     *   <li>IKE 提案必须同时含 PRF 和 DH 组，ESP 提案可省略 PRF 与 DH。</li>
     * </ul>
     */
    private void applyProposals(SettingsWriter writer) {
        if (mProposalTier >= MAX_PROPOSAL_TIER) {
            // 第 3 档：什么都不写，交给 androidbridge 的内置默认提案
            // （android_service.c：IKE 用 proposal_create_default / _default_aead，
            // ESP 用四组宽松提案，含 SHA-1，且 DH 带与不带两种都提供，由对端决定是否 PFS）。
            // 这就是改加密提案之前一直在用的行为，老服务端（如 strongSwan 5.6.3、
            // esp= 未配 DH）只能靠它协商成功。
            return;
        }
        if (mProposalTier == 0) {
            // 现代档：AEAD + ECP/Curve25519，无 SHA-1，无 MODP。
            // PRF 与 DH 按优先级并列，协商时取双方都支持的最靠前一项。
            // 与服务端 ike= 列表中的条目逐字一致（AEAD + SHA256 PRF + ECP256），保证能被服务端选中。
            writer.setValue("connection.ike_proposal",
                    "aes256gcm16-prfsha256-ecp256");
            writer.setValue("connection.esp_proposal",
                    "aes256gcm16-ecp384-ecp256-chacha20poly1305-curve25519");
        } else {
            // 兼容档：服务端较老、不认识 AEAD/ECP 时使用。
            // 与服务端 ike= 列表中的条目逐字一致（CBC + SHA384 + MODP2048），单条提案。
            writer.setValue("connection.ike_proposal",
                    "aes256-sha384-modp2048");
            writer.setValue("connection.esp_proposal",
                    "aes256-sha256-aes128-sha256");
        }
    }

    /**
     * 协商失败时降档重试一次。
     *
     * @return true 表示已安排重试；false 表示已是最后一档
     */
    private boolean retryWithFallbackProposal() {
        if (mProposalTier >= MAX_PROPOSAL_TIER) {
            return false;
        }
        mProposalTier++;
        LogUtil.i("Proposal negotiation failed, falling back to proposal tier " + mProposalTier
                + (mProposalTier >= MAX_PROPOSAL_TIER ? " (native defaults)" : ""));
        // 重新走一遍 conn()，它会用新的 tier 重新初始化 charon。
        if (mWorkHandler != null) {
            mWorkHandler.post(new ConnectJob());
        }
        return true;
    }

    /**
     * 检测native库是否损坏
     */
    private boolean isNativeLibraryCorrupted() {
        return nativeLibraryCorrupted;
    }
    
    /**
     * 标记native库为损坏状态
     */
    private void markNativeLibraryCorrupted() {
        LogUtil.e("Marking native library as corrupted - will require complete restart");
        nativeLibraryCorrupted = true;
        isInitialized = false;
    }
    
    /**
     * 重置native库损坏状态
     */
    private void resetNativeLibraryCorrupted() {
        LogUtil.i("Resetting native library corrupted state");
        nativeLibraryCorrupted = false;
    }

    /**
     * 清理所有连接状态
     */
    private void clearConnectionState() {
        LogUtil.i("Clearing connection state...");
        isInitialized = false;
        mIsDisconnecting = false;
        mCurrentProfile = null;
        // 注意：不重置nativeLibraryCorrupted，保持损坏状态直到服务重启
        // 注意：不在这里设置状态，让调用者决定状态
        LogUtil.i("Connection state cleared");
    }
    
    /**
     * 简单重置StrongSwan状态
     */
    private void resetStrongSwanState() {
        LogUtil.i("Resetting StrongSwan state...");
        
        // 1. 停止当前连接
        stopCurrentConnection();
        
        // 2. 清理所有状态
        clearConnectionState();
        
        // 3. 强制清理native库状态，防止插件加载失败
        forceCleanupNativeState();
        
        LogUtil.i("StrongSwan state reset completed");
    }
    
    /**
     * 强制清理native库状态，解决插件加载失败问题
     */
    private void forceCleanupNativeState() {
        try {
            LogUtil.i("Force cleaning up native library state...");
            
            // 多次调用deinitializeCharon确保完全清理
            for (int i = 0; i < 3; i++) {
                try {
                    safeNativeCall(() -> {
                        deinitializeCharon();
                        try {
                            Thread.sleep(200); // 给native层更多时间清理
                        } catch (InterruptedException ie) {
                            LogUtil.e("Interrupted during native cleanup sleep");
                        }
                    }, "forceCleanup_" + i);
                } catch (Exception e) {
                    LogUtil.i("Force cleanup attempt " + i + " completed with exception: " + e.getMessage());
                }
            }
            
            // 给系统更多时间完成清理
            Thread.sleep(1000);
            
            LogUtil.i("Native library state cleanup completed");
            
        } catch (Exception e) {
            LogUtil.e("Error during native library state cleanup: " + e.getMessage());
        }
    }

    /**
     * Stop any existing connection by deinitializing charon.
     * @param clearProfile whether to clear the current profile
     */
    private void stopCurrentConnection(boolean clearProfile) {
        LogUtil.i("stopCurrentConnection called with clearProfile=" + clearProfile + ", mCurrentProfile=" + (mCurrentProfile != null ? mCurrentProfile.getName() : "null"));
        if (mCurrentProfile != null) {
            LogUtil.i("Stopping current connection...");
            mIsDisconnecting = true;

            // 添加延迟，确保所有native操作完成
            try {
                Thread.sleep(100); // 给native线程一些时间完成操作
            } catch (InterruptedException e) {
                LogUtil.e("Interrupted while waiting for native operations to complete");
            }
            
            // 只有在已经初始化的情况下才调用deinitializeCharon
            if (isInitialized) {
                LogUtil.i("Deinitializing charon...");
                // 使用安全包装器调用native方法
                boolean success = safeNativeCall(() -> {
                    deinitializeCharon();
                    // 再次延迟，确保清理完成
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        LogUtil.e("Interrupted while waiting for cleanup");
                    }
                }, "deinitializeCharon");
                
                if (success) {
                    LogUtil.i("Charon deinitialized successfully");
                } else {
                    LogUtil.e("Failed to deinitialize charon");
                }
                isInitialized = false;
            } else {
                LogUtil.i("Charon not initialized, skipping deinitialization");
            }
            
            Log.i(TAG, "charon stopped");
            if (clearProfile) {
                LogUtil.i("Clearing mCurrentProfile as requested");
                mCurrentProfile = null;
            } else {
                LogUtil.i("Keeping mCurrentProfile as requested");
            }
        }
        LogUtil.i("stopCurrentConnection finished, mCurrentProfile=" + (mCurrentProfile != null ? mCurrentProfile.getName() : "null"));
        // 注意：不在这里设置状态，让调用者决定状态
    }

    /**
     * Stop any existing connection by deinitializing charon.
     * Default behavior is to clear the profile.
     */
    private void stopCurrentConnection() {
        stopCurrentConnection(true);
    }

    public void disconn() {
        LogUtil.i("charon stopped  mCurrentState=" + (mService != null ? mService.getState() : "null") + "  thread=" + Thread.currentThread().getName());
        setState(VpnStateService.State.DISCONNECTING);
        stopCurrentConnection();
        clearConnectionState(); // 清理所有状态
        forceUpdateState(VpnStateService.State.DISABLED); // 强制更新状态确保同步
    }

    public void conn() {
        if(mService==null){
            return;
        }
        Log.i(TAG, "charon started mCurrentState=" + mService.getState() + "  thread=" + Thread.currentThread().getName());
        LogUtil.i("conn() - mCurrentProfile before check: " + (mCurrentProfile != null ? mCurrentProfile.getName() : "null"));
        if (mCurrentProfile != null) {
            // 先停止现有连接，但不清空profile
            LogUtil.i("conn() - calling stopCurrentConnection(false)");
            stopCurrentConnection(false);
            LogUtil.i("conn() - mCurrentProfile after stopCurrentConnection: " + (mCurrentProfile != null ? mCurrentProfile.getName() : "null"));
            
            // 开始新连接
            startConnection(mCurrentProfile);
            mIsDisconnecting = false;
            LogUtil.i("conn() - creating BuilderAdapter with profile: " + (mCurrentProfile != null ? mCurrentProfile.getName() : "null"));
            BuilderAdapter builder = new BuilderAdapter(mCurrentProfile.getName(), mCurrentProfile.getSplitTunneling());
            
            // 添加详细的连接信息日志
            LogUtil.i("StrongSwan Client Version: 6.1.0");
            if (mCurrentProfile != null) {
                LogUtil.i("Server Gateway: " + mCurrentProfile.getGateway());
                LogUtil.i("BYOD Feature: " + mCurrentProfile.getVpnType().has(VpnType.VpnTypeFeature.BYOD));
                LogUtil.i("Connection Type: " + mCurrentProfile.getVpnType().getIdentifier());
            } else {
                LogUtil.e("mCurrentProfile is null!");
            }
            LogUtil.i("Log File: " + mLogFile);
            
            if (mCurrentProfile != null) {
                try {
                    // 检查native库是否损坏
                    if (isNativeLibraryCorrupted()) {
                        LogUtil.e("Native library is corrupted, cannot initialize StrongSwan");
                        LogUtil.e("Please restart the VPN service to recover from native library corruption");
                        setError(VpnStateService.ErrorState.GENERIC_ERROR);
                        return;
                    }
                    
                    // 检查是否已经初始化，避免重复初始化导致native状态污染
                    if (isInitialized) {
                        LogUtil.i("StrongSwan already initialized, skipping initialization");
                    } else {
                        LogUtil.i("Attempting to initialize StrongSwan...");
                        String appDir = getFilesDir().getAbsolutePath();
                        boolean initResult = initializeCharon(builder, mLogFile, appDir, mCurrentProfile.getVpnType().has(VpnType.VpnTypeFeature.BYOD), false);
                        
                        if (initResult) {
                            Log.i(TAG, "charon started");
                            isInitialized = true; // 标记为已初始化
                            SettingsWriter writer = new SettingsWriter();
                            writer.setValue("global.language", Locale.getDefault().getLanguage());
                            writer.setValue("global.mtu", mCurrentProfile.getMTU());
                            writer.setValue("connection.type", mCurrentProfile.getVpnType().getIdentifier());
                            writer.setValue("connection.server", mCurrentProfile.getGateway());
                            writer.setValue("connection.port", mCurrentProfile.getPort());
                            writer.setValue("connection.username", mCurrentProfile.getUsername());
                            writer.setValue("connection.password", mCurrentProfile.getPassword());
                            writer.setValue("connection.local_id", mCurrentProfile.getLocalId());
                            writer.setValue("connection.remote_id", mCurrentProfile.getRemoteId());
                            
                            /*
                             * 提案配置。
                             *
                             * androidbridge 只读取 connection.ike_proposal / connection.esp_proposal
                             * 两个键（见 libandroidbridge/backend/android_service.c 的 parse_proposal），
                             * 且每个键的值必须是一条**单一**提案：'-' 分隔该提案内部的算法，
                             * 不能出现逗号分隔的多提案列表（proposal_create_from_string 不认逗号）。
                             * charon.plugins.ikev2.* 这些键在上游并不存在（没有 ikev2 插件），
                             * 写了会被静默忽略。
                             *
                             * 所以在单条提案内用 '/' 表达同类算法的优先顺序（strongSwan 的
                             * print_alg 即用 '/' 连接同一 transform 的多个候选），
                             * 并把 DH 组按 ECP → Curve25519 → MODP 由强到弱排列。
                             *
                             * 同时注意 check_proposal 的约束：AEAD 与传统 CBC 加密不能混在同一条提案里，
                             * 因此现代档只用 AEAD，回退档只用 CBC。
                             */
                            applyProposals(writer);
                            
                            LogUtil.i("Initializing StrongSwan, proposal tier=" + mProposalTier);

                            // 使用安全包装器调用native方法
                            boolean success = safeNativeCall(() -> {
                                initiate(writer.serialize());
                                // 给连接一些时间建立
                                try {
                                    Thread.sleep(200);
                                } catch (InterruptedException e) {
                                    LogUtil.e("Interrupted while waiting for connection");
                                }
                            }, "initiate");
                            
                            if (!success) {
                                setError(VpnStateService.ErrorState.GENERIC_ERROR);
                            }
                        } else {
                            Log.e(TAG, "failed to start charon");
                            LogUtil.e("Failed to initialize StrongSwan - initializeCharon returned false");
                            
                            // 标记native库为损坏状态
                            markNativeLibraryCorrupted();
                            
                            // 不重试，直接报告错误
                            LogUtil.e("StrongSwan initialization failed - native library may be corrupted");
                            setError(VpnStateService.ErrorState.GENERIC_ERROR);
                        }
                    }
                } catch (Exception e) {
                    LogUtil.e("Exception during StrongSwan initialization: " + e.getMessage());
                    LogUtil.e("Stack trace: " + android.util.Log.getStackTraceString(e));
                    setError(VpnStateService.ErrorState.GENERIC_ERROR);
                }
            } else {
                LogUtil.e("Cannot initialize StrongSwan: mCurrentProfile is null");
                setError(VpnStateService.ErrorState.GENERIC_ERROR);
            }
        }
    }

    public void initButtonReceiver() {
        bReceiver = new ButtonBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_BUTTON);
        intentFilter.addAction(LOCATION_BUTTON);
        registerReceiver(bReceiver, intentFilter);
    }
    CommonResponse.ResponseOkListener serverListener = new CommonResponse.ResponseOkListener<ServerVo>() {
        @Override
        public void onResponse(ServerVo server) {
            if (server.hostList != null) {
                VpnProfile pro = DataBuilder.builderVpnProfile(server.expire, server.name, server.pwd, server.hostList.get(0));
                setNextProfile(pro);
            }

        }
    };
    CommonResponse.ResponseErrorListener serverListenerError = new CommonResponse.ResponseErrorListener() {
        @Override
        protected void onError() {
            super.onError();
            Toast.makeText(CharonVpnService.this, R.string.error_lookup_failed,Toast.LENGTH_SHORT).show();
        }
    };
    private void createForegroundService(boolean needConnecting) {
        if (nonIkev2ProtocolActive) {
            // 真正在连的是 WireGuard/VLESS：让出通知权，避免跟那条协议自己的
            // 前台通知同时出现、又显示着跟实际连接状态对不上的陈旧信息。
            LogUtil.i("createForegroundService: 非 IKEv2 协议激活中，跳过 IKEv2 通知");
            stopForeground(true);
            return;
        }
        LogUtil.i("start ForegroundService:" + mService.getState());
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(N_CHANNEL, "AFreedom", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("ShieldWing");
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            notificationManager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(MyApplication.getInstance(),N_CHANNEL);
        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.vpn_status_remote_view);
        remoteViews.setTextViewText(R.id.tv_vpn_time, LocationUtil.getSelectName(this));
        boolean going = false;
        if (VpnStateService.State.CONNECTED.equals(mService.getState())) {
            remoteViews.setImageViewResource(R.id.btn_vpn, R.drawable.vpn_on);
            remoteViews.setTextViewText(R.id.tv_vpn_content, getString(R.string.vpn_remote_on));
            remoteViews.setViewVisibility(R.id.btn_vpn, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.rb_conning, View.GONE);
            VPN_STATUS_NOTIF = true;
            going = true;
        } else if (VpnStateService.State.CONNECTING.equals(mService.getState()) || needConnecting) {
            remoteViews.setImageViewResource(R.id.btn_vpn, R.drawable.vpn_off);
            remoteViews.setTextViewText(R.id.tv_vpn_content, getString(R.string.vpn_remote_ing));
            remoteViews.setViewVisibility(R.id.btn_vpn, View.GONE);
            remoteViews.setViewVisibility(R.id.rb_conning, View.VISIBLE);
            VPN_STATUS_NOTIF = false;
            going = true;
        } else {
            remoteViews.setImageViewResource(R.id.btn_vpn, R.drawable.vpn_off);
            remoteViews.setTextViewText(R.id.tv_vpn_content, getString(R.string.vpn_remote_off));
            remoteViews.setViewVisibility(R.id.btn_vpn, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.rb_conning, View.GONE);
            if(!PreferenceUtils.getPrefBoolean(this, Constants.NOTIFY_SWITCH, true) ) {
                stopForeground(true);
                return;
            }
            VPN_STATUS_NOTIF = false;
            going = false;
        }
        //点击的事件处理
        Intent buttonIntent = new Intent(ACTION_BUTTON);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 1, buttonIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.btn_vpn, pendingIntent);

        Intent locationIntent = new Intent(LOCATION_BUTTON);
        locationIntent.addFlags(FLAG_ACTIVITY_NEW_TASK );

        PendingIntent locationPending = PendingIntent.getBroadcast(this, 1, locationIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.tv_vpn_time, locationPending);

        Intent intent = new Intent(this, MainFragmentViewPage.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pend =
                PendingIntent.getActivity(MyApplication.getInstance(), new Random().nextInt(), intent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent intentCancel = new Intent(this, NotificationBroadcastReceiver.class);
        intentCancel.setAction(NotificationBroadcastReceiver.CANNCELL_ACTION);
        PendingIntent pandCanel =
                PendingIntent.getActivity(MyApplication.getInstance(), new Random().nextInt(), intentCancel, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentIntent(pend)
                .setDeleteIntent(pandCanel)
                .setContent(remoteViews)
                .setWhen(System.currentTimeMillis())// 通知产生的时间，会在通知信息里显示
                .setTicker("ShieldWing start")
                .setOngoing(going)
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.vpn_on)
        .setVibrate(new long[]{0})
        .setSound(null);
        Notification notify = builder.build();
        notify.flags = Notification.FLAG_ONGOING_EVENT;
        startForeground(FOREGROUND_NOTIFY_ID, notify);
    }
    public class NotificationBroadcastReceiver extends BroadcastReceiver {
        public static final String CANNCELL_ACTION="notification_cancelled";
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (CANNCELL_ACTION.equals(action)) {
                stopForeground(true);
            }

        }
    }
    /**
     *
     * 收起通知栏
     * @param context
     */
    public static void collapseStatusBar(Context context) {
        try {
            Object statusBarManager = context.getSystemService("statusbar");
            Method collapse;
            if (Build.VERSION.SDK_INT <= 16) {
                collapse = statusBarManager.getClass().getMethod("collapse");
            } else {
                collapse = statusBarManager.getClass().getMethod("collapsePanels");
            }
            collapse.invoke(statusBarManager);
        } catch (Exception e) {
            LogUtil.e(e);
        }
    }
    public void vpnClick(){
        int id = LocationUtil.getSelectLocationId(this);
        indexService.getData(String.format(Constants.getUrl(Constants.API_SERVERLIST_URL), id), serverListener, serverListenerError, VPN_SERVER_CLICK, ServerVo.class);
        createForegroundService(true);
    }

    public class ButtonBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_BUTTON) && mService!=null) {
//                EventBusUtil.getEventBus().post(new VpnClickEvent());
                LogUtil.i("recive vpn Broadcast: " + mService.getState() + "; mCurrentProfile=" + mCurrentProfile);
                if (nonIkev2ProtocolActive) {
                    // 实际在跑的是 WireGuard/VLESS：这条通知上的按钮必须断那个隧道，
                    // 不能走下面的 IKEv2 逻辑（见 activeEngineDisconnect 的注释）。
                    Runnable disconnect = activeEngineDisconnect;
                    LogUtil.i("通知按钮：转交当前活跃引擎断开");
                    if (disconnect != null) {
                        disconnect.run();
                    }
                    return;
                }
                if (VpnStateService.State.CONNECTED.equals(mService.getState())) {
                    setNextProfile(null);
                } else {
//                    if (mCurrentProfile != null)
//                        setNextProfile(mCurrentProfile);
                    vpnClick();
                }
            } else if (action.equals(LOCATION_BUTTON)) {
                LocationPageViewFragment.startFragment(CharonVpnService.this);
                collapseStatusBar(CharonVpnService.this);
            }

        }
    }

    public class DisConnectJob implements Runnable {
        @Override
        public void run() {
            disconn();
        }
    }

    public class ConnectJob implements Runnable {
        @Override
        public void run() {
            conn();
        }
    }

    public class BuilderAdapter {
        private final String mName;
        private final Integer mSplitTunneling;
        private Builder mBuilder;
        private BuilderCache mCache;
        private BuilderCache mEstablishedCache;

        public BuilderAdapter(String name, Integer splitTunneling) {
            mName = name;
            mSplitTunneling = splitTunneling;
            mBuilder = createBuilder(name);
            mCache = new BuilderCache(mSplitTunneling);
        }

        private Builder createBuilder(String name) {
            Builder builder = new Builder();
            builder.setSession(mName);
            applySplitTunneling(builder);

			/* even though the option displayed in the system dialog says "Configure"
			 * we just use our main Activity */
            Context context = getApplicationContext();
            Intent intent = new Intent(context, MainFragmentViewPage.class);
            PendingIntent pending = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setConfigureIntent(pending);
            return builder;
        }

        /**
         * 按 VpnProfile 上的分流策略设置 include/exclude 应用。
         * <p>
         * 系统限制：同一个 Builder 上 include 与 exclude 互斥；
         * 且 include/exclude 必须在 address/route 之前设置才不会互相影响。
         */
        private void applySplitTunneling(Builder builder) {
            VpnProfile profile = mCurrentProfile;
            if (profile == null) {
                return;
            }
            java.util.LinkedHashSet<String> pkgs = profile.getSplitPackages();
            if (pkgs == null || pkgs.isEmpty()) {
                return;
            }
            boolean include = "include".equalsIgnoreCase(profile.getSplitMode());
            for (String pkg : pkgs) {
                try {
                    if (include) {
                        builder.addAllowedApplication(pkg);
                    } else {
                        builder.addDisallowedApplication(pkg);
                    }
                } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                    // 应用已卸载：跳过而不是让整次连接失败。
                    LogUtil.w("split tunneling: 包不存在 " + pkg);
                }
            }
        }

        public synchronized boolean addAddress(String address, int prefixLength) {
            try {
                mCache.addAddress(address, prefixLength);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean addDnsServer(String address) {
            try {
                mBuilder.addDnsServer(address);
                mCache.recordAddressFamily(address);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean addRoute(String address, int prefixLength) {
            try {
                mCache.addRoute(address, prefixLength);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean addSearchDomain(String domain) {
            try {
                mBuilder.addSearchDomain(domain);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean setMtu(int mtu) {
            try {
                mCache.setMtu(mtu);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized int establish() {
            ParcelFileDescriptor fd;
            try {
                mCache.applyData(mBuilder);
                fd = mBuilder.establish();
            } catch (Exception ex) {
                ex.printStackTrace();
                return -1;
            }
            if (fd == null) {
                return -1;
            }
			/* now that the TUN device is created we don't need the current
			 * builder anymore, but we might need another when reestablishing */
            mBuilder = createBuilder(mName);
            mEstablishedCache = mCache;
            mCache = new BuilderCache(mSplitTunneling);
            return fd.detachFd();
        }

        public synchronized int establishNoDns() {
            ParcelFileDescriptor fd;

            if (mEstablishedCache == null) {
                return -1;
            }
            try {
                Builder builder = createBuilder(mName);
                mEstablishedCache.applyData(builder);
                fd = builder.establish();
            } catch (Exception ex) {
                ex.printStackTrace();
                return -1;
            }
            if (fd == null) {
                return -1;
            }
            return fd.detachFd();
        }
    }

    /**
     * Cache non DNS related information so we can recreate the builder without
     * that information when reestablishing IKE_SAs
     */
    public class BuilderCache {
        private final List<PrefixedAddress> mAddresses = new ArrayList<>();
        private final List<PrefixedAddress> mRoutesIPv4 = new ArrayList<>();
        private final List<PrefixedAddress> mRoutesIPv6 = new ArrayList<>();
        private final int mSplitTunneling;
        private int mMtu;
        private boolean mIPv4Seen, mIPv6Seen;

        public BuilderCache(Integer splitTunneling) {
            mSplitTunneling = splitTunneling != null ? splitTunneling : 0;
        }

        public void addAddress(String address, int prefixLength) {
            mAddresses.add(new PrefixedAddress(address, prefixLength));
            recordAddressFamily(address);
        }

        public void addRoute(String address, int prefixLength) {
            try {
                if (isIPv6(address)) {
                    mRoutesIPv6.add(new PrefixedAddress(address, prefixLength));
                } else {
                    mRoutesIPv4.add(new PrefixedAddress(address, prefixLength));
                }
            } catch (UnknownHostException ex) {
                ex.printStackTrace();
            }
        }

        public void setMtu(int mtu) {
            mMtu = mtu;
        }

        public void recordAddressFamily(String address) {
            try {
                if (isIPv6(address)) {
                    mIPv6Seen = true;
                } else {
                    mIPv4Seen = true;
                }
            } catch (UnknownHostException ex) {
                ex.printStackTrace();
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void applyData(Builder builder) {
            for (PrefixedAddress address : mAddresses) {
                builder.addAddress(address.mAddress, address.mPrefix);
            }
			/* add routes depending on whether split tunneling is allowed or not,
			 * that is, whether we have to handle and block non-VPN traffic */
            if ((mSplitTunneling & VpnProfile.SPLIT_TUNNELING_BLOCK_IPV4) == 0) {
                if (mIPv4Seen) {	/* split tunneling is used depending on the routes */
                    for (PrefixedAddress route : mRoutesIPv4) {
                        builder.addRoute(route.mAddress, route.mPrefix);
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {	/* allow traffic that would otherwise be blocked to bypass the VPN */
                    builder.allowFamily(OsConstants.AF_INET);
                }
            } else if (mIPv4Seen) {	/* only needed if we've seen any addresses.  otherwise, traffic
				 * is blocked by default (we also install no routes in that case) */
                builder.addRoute("0.0.0.0", 0);
            }
			/* same thing for IPv6 */
            if ((mSplitTunneling & VpnProfile.SPLIT_TUNNELING_BLOCK_IPV6) == 0) {
                if (mIPv6Seen) {
                    for (PrefixedAddress route : mRoutesIPv6) {
                        builder.addRoute(route.mAddress, route.mPrefix);
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    builder.allowFamily(OsConstants.AF_INET6);
                }
            } else if (mIPv6Seen) {
                builder.addRoute("::", 0);
            }
            builder.setMtu(mMtu);
        }

        private boolean isIPv6(String address) throws UnknownHostException {
            InetAddress addr = InetAddress.getByName(address);
            if (addr instanceof Inet4Address) {
                return false;
            } else if (addr instanceof Inet6Address) {
                return true;
            }
            return false;
        }

        private class PrefixedAddress {
            public String mAddress;
            public int mPrefix;

            public PrefixedAddress(String address, int prefix) {
                this.mAddress = address;
                this.mPrefix = prefix;
            }
        }
    }
}
