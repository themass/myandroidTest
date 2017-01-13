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
import android.support.v7.app.NotificationCompat;
import android.system.OsConstants;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.timeline.vpn.R;
import com.timeline.vpn.base.MyApplication;
import com.timeline.vpn.bean.vo.VpnProfile;
import com.timeline.vpn.common.util.FileUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.data.LocationUtil;
import com.timeline.vpn.ui.main.MainFragment;

import org.strongswan.android.logic.imc.ImcState;
import org.strongswan.android.logic.imc.RemediationInstruction;
import org.strongswan.android.logic.utils.SettingsWriter;

import java.io.InputStream;
import java.io.StringBufferInputStream;
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

public class CharonVpnService extends VpnService implements VpnStateService.VpnStateListener {
    public static final String PROFILE = "PROFILE";
    public static final int FOREGROUND_NOTIFY_ID = 100;
    /**
     * 通知栏按钮点击事件对应的ACTION
     */
    public final static String ACTION_BUTTON = "com.vpn.notifications.intent.action.ButtonClick";
    public final static String INTENT_CLICK_TAG = "ClickId";
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

    /*
     * The libraries are extracted to /data/data/org.strongswan.android/...
     * during installation.  On newer releases most are loaded in JNI_OnLoad.
     */
    static {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
        {
            System.loadLibrary("strongswan");
            System.loadLibrary("tpmtss");
            System.loadLibrary("tncif");
            System.loadLibrary("tnccs");
            System.loadLibrary("imcv");
            System.loadLibrary("charon");
            System.loadLibrary("ipsec");
        }
        System.loadLibrary("androidbridge");
    }

    private final Object mServiceLock = new Object();
    public ButtonBroadcastReceiver bReceiver;
    private String mLogFile;
    private VpnProfile mCurrentProfile;
    private volatile boolean mIsDisconnecting;
    private HandlerThread mWorkThread;
    private Handler mWorkHandler;
    private VpnStateService mService;
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
        }
    };
    private int retry = 0;

    @Override
    public void stateChanged() {
        createForegroundService();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Bundle bundle = intent.getExtras();
            VpnProfile profile = null;
            if (bundle != null) {
                profile = (VpnProfile) bundle.getSerializable(PROFILE);
            }
            if (profile != null)
                mCurrentProfile = profile;
            setNextProfile(profile);
        }
        LogUtil.i("charon service onStartCommand");
        return START_NOT_STICKY;
    }

    private void setNextProfile(VpnProfile profile) {
        if (mWorkHandler != null) {
            if (profile == null) {
                mWorkHandler.post(new DisConnectJob());
            } else {
                mWorkHandler.post(new ConnectJob());
            }
        } else {
            LogUtil.w("启动没完成");
        }
    }

    @Override
    public void onCreate() {
        mLogFile = FileUtils.getCharonFilePath();
        /* use a separate thread as main thread for charon */
		/* the thread is started when the service is bound */
        bindService(new Intent(this, VpnStateService.class),
                mServiceConnection, Service.BIND_AUTO_CREATE);
        mWorkThread = new HandlerThread(WORK_ANME);
        initButtonReceiver();
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
                if (!mIsDisconnecting) {
                    mService.setError(error);
                }
            }
        }
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
                setState(VpnStateService.State.CONNECTED);
                break;
            case STATE_AUTH_ERROR:
                setErrorDisconnect(VpnStateService.ErrorState.AUTH_FAILED);
                break;
            case STATE_PEER_AUTH_ERROR:
                setErrorDisconnect(VpnStateService.ErrorState.PEER_AUTH_FAILED);
                break;
            case STATE_LOOKUP_ERROR:
                setErrorDisconnect(VpnStateService.ErrorState.LOOKUP_FAILED);
                break;
            case STATE_UNREACHABLE_ERROR:
                setErrorDisconnect(VpnStateService.ErrorState.UNREACHABLE);
                break;
            case STATE_GENERIC_ERROR:
                setErrorDisconnect(VpnStateService.ErrorState.GENERIC_ERROR);
                break;
            default:
                Log.e(TAG, "Unknown status code received");
                break;
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
     * @param byod    enable BYOD features
     * @return TRUE if initialization was successful
     */
    public native boolean initializeCharon(BuilderAdapter builder, String logfile, boolean byod);

    /**
     * Deinitialize charon, provided by libandroidbridge.so
     */
    public native void deinitializeCharon();

    /**
     * Initiate VPN, provided by libandroidbridge.so
     */
    public native void initiate(String config);

    /**
     * Adapter for VpnService.Builder which is used to access it safely via JNI.
     * There is a corresponding C object to access it from native code.
     */
    public void disconn() {
        LogUtil.i("charon stopped  mCurrentState=" + mService.getState() + "  thread=" + Thread.currentThread().getName());
        setState(VpnStateService.State.DISCONNECTING);
        mIsDisconnecting = true;
        deinitializeCharon();
        Log.i(TAG, "charon stopped");
        setState(VpnStateService.State.DISABLED);
    }

    public void conn() {
        Log.i(TAG, "charon started mCurrentState=" + mService.getState() + "  thread=" + Thread.currentThread().getName());
        if (mCurrentProfile != null) {
            startConnection(mCurrentProfile);
            mIsDisconnecting = false;
            BuilderAdapter builder = new BuilderAdapter(mCurrentProfile.getName(), mCurrentProfile.getSplitTunneling());
            if (initializeCharon(builder, mLogFile, mCurrentProfile.getVpnType().has(VpnType.VpnTypeFeature.BYOD))) {
                Log.i(TAG, "charon started");
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
                initiate(writer.serialize());
            } else {
                Log.e(TAG, "failed to start charon");
                setError(VpnStateService.ErrorState.GENERIC_ERROR);
                //setState(VpnStateService.State.DISABLED);
            }
        }
    }

    public void initButtonReceiver() {
        bReceiver = new ButtonBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_BUTTON);
        registerReceiver(bReceiver, intentFilter);
    }

    private void createForegroundService() {
        LogUtil.i("start ForegroundService:" + mService.getState());
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(MyApplication.getInstance());
        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.remote_view);
        remoteViews.setTextViewText(R.id.tv_vpn_title, getString(R.string.vpn_remote_status));
        remoteViews.setTextViewText(R.id.tv_vpn_time, LocationUtil.getSelectName(this));
        boolean canGo = true;
        if (VpnStateService.State.CONNECTED.equals(mService.getState())) {
            remoteViews.setImageViewResource(R.id.btn_vpn, R.drawable.remote_vpn_on);
            remoteViews.setTextViewText(R.id.tv_vpn_content, getString(R.string.vpn_remote_on));
            remoteViews.setViewVisibility(R.id.btn_vpn, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.rb_conning, View.GONE);
            VPN_STATUS_NOTIF = true;
            canGo = false;
        } else if (VpnStateService.State.CONNECTING.equals(mService.getState())) {
//            remoteViews.setImageViewResource(R.id.btn_vpn, R.drawable.remote_vpn_off);
            remoteViews.setTextViewText(R.id.tv_vpn_content, getString(R.string.vpn_remote_ing));
            remoteViews.setViewVisibility(R.id.btn_vpn, View.GONE);
            remoteViews.setViewVisibility(R.id.rb_conning, View.VISIBLE);
            VPN_STATUS_NOTIF = false;
        } else {
            remoteViews.setImageViewResource(R.id.btn_vpn, R.drawable.remote_vpn_off);
            remoteViews.setTextViewText(R.id.tv_vpn_content, getString(R.string.vpn_remote_off));
            remoteViews.setViewVisibility(R.id.btn_vpn, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.rb_conning, View.GONE);
            VPN_STATUS_NOTIF = false;
        }
        //点击的事件处理
        Intent buttonIntent = new Intent(ACTION_BUTTON);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 1, buttonIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.btn_vpn, pendingIntent);
        Intent intent = new Intent(this, MainFragment.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pend =
                PendingIntent.getActivity(MyApplication.getInstance(), new Random().nextInt(), intent, 0);
        builder.setContentIntent(pend)
                .setContent(remoteViews)
                .setWhen(System.currentTimeMillis())// 通知产生的时间，会在通知信息里显示
                .setTicker("FreeVPN start")
                .setOngoing(canGo)
                .setSmallIcon(R.drawable.remote_vpn_on);
        Notification notify = builder.build();
        notify.flags = Notification.FLAG_ONGOING_EVENT;
        startForeground(FOREGROUND_NOTIFY_ID, notify);
    }

    public class ButtonBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_BUTTON)) {
                LogUtil.i("recive vpn Broadcast: " + mService.getState() + "; mCurrentProfile=" + mCurrentProfile);
                if (VpnStateService.State.CONNECTED.equals(mService.getState())) {
                    setNextProfile(null);
                } else {
                    if (mCurrentProfile != null)
                        setNextProfile(mCurrentProfile);
                }
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

			/* even though the option displayed in the system dialog says "Configure"
			 * we just use our main Activity */
            Context context = getApplicationContext();
            Intent intent = new Intent(context, MainFragment.class);
            PendingIntent pending = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setConfigureIntent(pending);
            return builder;
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
        private final List<PrefixedAddress> mAddresses = new ArrayList<PrefixedAddress>();
        private final List<PrefixedAddress> mRoutesIPv4 = new ArrayList<PrefixedAddress>();
        private final List<PrefixedAddress> mRoutesIPv6 = new ArrayList<PrefixedAddress>();
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
