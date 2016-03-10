///*
// * Copyright (C) 2012-2015 Tobias Brunner
// * Copyright (C) 2012 Giuliano Grassi
// * Copyright (C) 2012 Ralf Sager
// * Hochschule fuer Technik Rapperswil
// *
// * This program is free software; you can redistribute it and/or modify it
// * under the terms of the GNU General Public License as published by the
// * Free Software Foundation; either version 2 of the License, or (at your
// * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
// *
// * This program is distributed in the hope that it will be useful, but
// * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
// * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// * for more details.
// */
//
//package com.timeline.vpn.service;
//
//import android.annotation.TargetApi;
//import android.app.PendingIntent;
//import android.app.Service;
//import android.content.ComponentName;
//import android.content.Context;
//import android.content.Intent;
//import android.content.ServiceConnection;
//import android.net.VpnService;
//import android.os.Build;
//import android.os.Handler;
//import android.os.HandlerThread;
//import android.os.IBinder;
//import android.os.ParcelFileDescriptor;
//import android.security.KeyChain;
//import android.security.KeyChainException;
//import android.system.OsConstants;
//
//import com.timeline.vpn.api.NetApi;
//import com.timeline.vpn.common.util.LogUtil;
//import com.timeline.vpn.service.VpnStateService.ErrorState;
//import com.timeline.vpn.service.VpnStateService.State;
//import com.timeline.vpn.service.utils.SettingsWriter;
//import com.timeline.vpn.ui.VpnManagerActivity;
//
//import java.io.File;
//import java.io.StringBufferInputStream;
//import java.net.Inet4Address;
//import java.net.Inet6Address;
//import java.net.InetAddress;
//import java.net.UnknownHostException;
//import java.security.PrivateKey;
//import java.security.cert.Certificate;
//import java.security.cert.CertificateEncodingException;
//import java.security.cert.CertificateFactory;
//import java.security.cert.X509Certificate;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Locale;
//
//public class CharonVpnServicebak extends VpnService {
//    public static final int COMMOND_DEFAULT = 0;
//    public static final int COMMOND_START = 1;
//    public static final int COMMOND_STOP = 2;
//    public static final String COMMOND = "commond";
//    public static final String LOCATION = "location";
//    public static final String LOG_FILE = "charon.log";
//    private static final String WORK_NAME = "vpn_thread";
//    private String mLogFile;
//    private HandlerThread workThread;
//    private Handler workHandle;
//    private volatile boolean mIsDisconnecting;
//    private final Object mServiceLock = new Object();
//    private org.strongswan.android.logic.VpnStateService mService;
//
//    static {
//        System.loadLibrary("strongswan");
//        System.loadLibrary("charon");
//        System.loadLibrary("ipsec");
//        System.loadLibrary("hydra");
//        System.loadLibrary("androidbridge");
//    }
//
//    /**
//     * as defined in charonservice.h
//     */
//    static final int STATE_CHILD_SA_UP = 1;
//    static final int STATE_CHILD_SA_DOWN = 2;
//    static final int STATE_AUTH_ERROR = 3;
//    static final int STATE_PEER_AUTH_ERROR = 4;
//    static final int STATE_LOOKUP_ERROR = 5;
//    static final int STATE_UNREACHABLE_ERROR = 6;
//    static final int STATE_GENERIC_ERROR = 7;
//    private final ServiceConnection mServiceConnection = new ServiceConnection() {
//        @Override
//        public void onServiceDisconnected(ComponentName name) {
//            synchronized (mServiceLock) {
//                mService = null;
//            }
//        }
//
//        @Override
//        public void onServiceConnected(ComponentName name, IBinder service) {
//            synchronized (mServiceLock) {
//                mService = ((org.strongswan.android.logic.VpnStateService.LocalBinder) service).getService();
//            }
//        }
//    };
//
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        if (intent != null) {
//            int i = intent.getIntExtra(COMMOND, COMMOND_DEFAULT);
//            switch (i) {
//                case COMMOND_START:
//                    startConnection();
//                    break;
//                case COMMOND_STOP:
//                    stopCurrentConnection();
//                    break;
//                default:
//                    break;
//            }
//        }
//        return START_NOT_STICKY;
//    }
//
//    @Override
//    public void onCreate() {
//        mLogFile = getFilesDir().getAbsolutePath() + File.separator + LOG_FILE;
//        workThread = new HandlerThread(WORK_NAME);
//        workThread.start();
//        workHandle = new Handler(workThread.getLooper());
//        bindService(new Intent(this, org.strongswan.android.logic.VpnStateService.class),
//                mServiceConnection, Service.BIND_AUTO_CREATE);
//    }
//
//    @Override
//    public void onRevoke() {	/* the system revoked the rights grated with the initial prepare() call.
//         * called when the user clicks disconnect in the system's VPN dialog */
//        stopCurrentConnection();
//    }
//
//    @Override
//    public void onDestroy() {
//        workThread.quit();
//    }
//
//    /**
//     * Stop any existing connection by deinitializing charon.
//     */
//    public void stopCurrentConnection() {
//        synchronized (this) {
//            setState(State.DISCONNECTING);
//            mIsDisconnecting = true;
//            deinitializeCharon();
//            LogUtil.i("charon stopped ...");
//        }
//    }
//
//    /**
//     * Notify the state service about a new connection attempt.
//     * Called by the handler thread.
//     */
//    private void startConnection() {
//        setState(State.CONNECTING);
//        mIsDisconnecting = false;
//
//        workHandle.post(new CharonVpnServicebak.VpnConnectJob(this));
//    }
//
//    /**
//     * Update the current VPN state on the state service. Called by the handler
//     * thread and any of charon's threads.
//     *
//     * @param state current state
//     */
//    private void setState(State state) {
//        synchronized (mServiceLock) {
//            if (mService != null) {
//                mService.setState(state);
//            }
//        }
//    }
//
//    /**
//     * Set an error on the state service. Called by the handler thread and any
//     * of charon's threads.
//     *
//     * @param error error state
//     */
//    private void setError(ErrorState error) {
//        synchronized (mServiceLock) {
//            if (mService != null) {
//                mService.setError(error);
//            }
//        }
//    }
//
//    public void updateImcState(int value) {
//        LogUtil.i("updateImcState->" + value);
//    }
//
//    public void updateStatus(int status) {
//        switch (status) {
//            case STATE_CHILD_SA_DOWN:
//                if (!mIsDisconnecting) {
//                    setState(State.CONNECTING);
//                }
//                break;
//            case STATE_CHILD_SA_UP:
//                setState(State.CONNECTED);
//                break;
//            case STATE_AUTH_ERROR:
//                setError(ErrorState.AUTH_FAILED);
//                break;
//            case STATE_PEER_AUTH_ERROR:
//                setError(ErrorState.PEER_AUTH_FAILED);
//                break;
//            case STATE_LOOKUP_ERROR:
//                setError(ErrorState.LOOKUP_FAILED);
//                break;
//            case STATE_UNREACHABLE_ERROR:
//                setError(ErrorState.UNREACHABLE);
//                break;
//            case STATE_GENERIC_ERROR:
//                setError(ErrorState.GENERIC_ERROR);
//                break;
//            default:
//                LogUtil.i("Unknown status code received");
//                break;
//        }
//    }
//
//    /**
//     * Function called via JNI to generate a list of DER encoded CA certificates
//     * as byte array.
//     *
//     * @return a list of DER encoded CA certificates
//     */
//    private byte[][] getTrustedCertificates() {
//        ArrayList localArrayList = new ArrayList();
//        try {
//            CertificateFactory localCertificateFactory = CertificateFactory.getInstance("X.509");
//            Certificate localCertificate = localCertificateFactory.generateCertificate(new StringBufferInputStream(NetApi.getCert()));
//            localArrayList.add(localCertificate.getEncoded());
//            return (byte[][]) localArrayList.toArray(new byte[localArrayList.size()][]);
//        } catch (Exception e) {
//            LogUtil.e(e);
//            return null;
//        }
//    }
//
//    /**
//     * Function called via JNI to get a list containing the DER encoded certificates
//     * of the user selected certificate chain (beginning with the user certificate).
//     * <p/>
//     * Since this method is called from a thread of charon's thread pool we are safe
//     * to call methods on KeyChain directly.
//     *
//     * @return list containing the certificates (first element is the user certificate)
//     * @throws InterruptedException
//     * @throws KeyChainException
//     * @throws CertificateEncodingException
//     */
//    private byte[][] getUserCertificate() throws KeyChainException, InterruptedException, CertificateEncodingException {
//        ArrayList<byte[]> encodings = new ArrayList<byte[]>();
//        X509Certificate[] chain = KeyChain.getCertificateChain(getApplicationContext(), null);
//        if (chain == null || chain.length == 0) {
//            return null;
//        }
//        for (X509Certificate cert : chain) {
//            encodings.add(cert.getEncoded());
//        }
//        return encodings.toArray(new byte[encodings.size()][]);
//    }
//
//    /**
//     * Function called via JNI to get the private key the user selected.
//     * <p/>
//     * Since this method is called from a thread of charon's thread pool we are safe
//     * to call methods on KeyChain directly.
//     *
//     * @return the private key
//     * @throws InterruptedException
//     * @throws KeyChainException
//     * @throws CertificateEncodingException
//     */
//    private PrivateKey getUserKey() throws KeyChainException, InterruptedException {
//        return KeyChain.getPrivateKey(getApplicationContext(), null);
//    }
//
//    /**
//     * Initialization of charon, provided by libandroidbridge.so
//     *
//     * @param builder BuilderAdapter for this connection
//     * @param logfile absolute path to the logfile
//     * @return TRUE if initialization was successful
//     */
//    public native boolean initializeCharon(BuilderAdapter builder, String logfile, boolean byod);
//
//    /**
//     * Deinitialize charon, provided by libandroidbridge.so
//     */
//    public native void deinitializeCharon();
//
//    /**
//     * Initiate VPN, provided by libandroidbridge.so
//     */
//    public native void initiate(String config);
//
//    /**
//     * Adapter for VpnService.Builder which is used to access it safely via JNI.
//     * There is a corresponding C object to access it from native code.
//     */
//    public class BuilderAdapter {
//        private final String mName;
//        private final Integer mSplitTunneling;
//        private Builder mBuilder;
//        private BuilderCache mCache;
//        private BuilderCache mEstablishedCache;
//
//        public BuilderAdapter(String name, Integer splitTunneling) {
//            mName = name;
//            mSplitTunneling = splitTunneling;
//            mBuilder = createBuilder(name);
//            mCache = new BuilderCache(mSplitTunneling);
//        }
//
//        private Builder createBuilder(String name) {
//            Builder builder = new Builder();
//            builder.setSession(mName);
//
//			/* even though the option displayed in the system dialog says "Configure"
//			 * we just use our main Activity */
//            Context context = getApplicationContext();
//            Intent intent = new Intent(context, VpnManagerActivity.class);
//            PendingIntent pending = PendingIntent.getActivity(context, 0, intent,
//                    PendingIntent.FLAG_UPDATE_CURRENT);
//            builder.setConfigureIntent(pending);
//            return builder;
//        }
//
//        public synchronized boolean addAddress(String address, int prefixLength) {
//            try {
//                mCache.addAddress(address, prefixLength);
//            } catch (IllegalArgumentException ex) {
//                return false;
//            }
//            return true;
//        }
//
//        public synchronized boolean addDnsServer(String address) {
//            try {
//                mBuilder.addDnsServer(address);
//                mCache.recordAddressFamily(address);
//            } catch (IllegalArgumentException ex) {
//                return false;
//            }
//            return true;
//        }
//
//        public synchronized boolean addRoute(String address, int prefixLength) {
//            try {
//                mCache.addRoute(address, prefixLength);
//            } catch (IllegalArgumentException ex) {
//                return false;
//            }
//            return true;
//        }
//
//        public synchronized boolean addSearchDomain(String domain) {
//            try {
//                mBuilder.addSearchDomain(domain);
//            } catch (IllegalArgumentException ex) {
//                return false;
//            }
//            return true;
//        }
//
//        public synchronized boolean setMtu(int mtu) {
//            try {
//                mCache.setMtu(mtu);
//            } catch (IllegalArgumentException ex) {
//                return false;
//            }
//            return true;
//        }
//
//        public synchronized int establish() {
//            ParcelFileDescriptor fd;
//            try {
//                mCache.applyData(mBuilder);
//                fd = mBuilder.establish();
//            } catch (Exception ex) {
//                LogUtil.e(ex);
//                return -1;
//            }
//            if (fd == null) {
//                return -1;
//            }
//			/* now that the TUN device is created we don't need the current
//			 * builder anymore, but we might need another when reestablishing */
//            mBuilder = createBuilder(mName);
//            mEstablishedCache = mCache;
//            mCache = new BuilderCache(mSplitTunneling);
//            return fd.detachFd();
//        }
//
//        public synchronized int establishNoDns() {
//            ParcelFileDescriptor fd;
//
//            if (mEstablishedCache == null) {
//                return -1;
//            }
//            try {
//                Builder builder = createBuilder(mName);
//                mEstablishedCache.applyData(builder);
//                fd = builder.establish();
//            } catch (Exception ex) {
//                LogUtil.e(ex);
//                return -1;
//            }
//            if (fd == null) {
//                return -1;
//            }
//            return fd.detachFd();
//        }
//    }
//
//    /**
//     * Cache non DNS related information so we can recreate the builder without
//     * that information when reestablishing IKE_SAs
//     */
//    public class BuilderCache {
//        private final List<PrefixedAddress> mAddresses = new ArrayList<PrefixedAddress>();
//        private final List<PrefixedAddress> mRoutesIPv4 = new ArrayList<PrefixedAddress>();
//        private final List<PrefixedAddress> mRoutesIPv6 = new ArrayList<PrefixedAddress>();
//        private final int mSplitTunneling;
//        private int mMtu;
//        private boolean mIPv4Seen, mIPv6Seen;
//
//        public BuilderCache(Integer splitTunneling) {
//            mSplitTunneling = splitTunneling != null ? splitTunneling : 0;
//        }
//
//        public void addAddress(String address, int prefixLength) {
//            mAddresses.add(new PrefixedAddress(address, prefixLength));
//            recordAddressFamily(address);
//        }
//
//        public void addRoute(String address, int prefixLength) {
//            try {
//                if (isIPv6(address)) {
//                    mRoutesIPv6.add(new PrefixedAddress(address, prefixLength));
//                } else {
//                    mRoutesIPv4.add(new PrefixedAddress(address, prefixLength));
//                }
//            } catch (UnknownHostException ex) {
//                ex.printStackTrace();
//            }
//        }
//
//        public void setMtu(int mtu) {
//            mMtu = mtu;
//        }
//
//        public void recordAddressFamily(String address) {
//            try {
//                if (isIPv6(address)) {
//                    mIPv6Seen = true;
//                } else {
//                    mIPv4Seen = true;
//                }
//            } catch (UnknownHostException ex) {
//                ex.printStackTrace();
//            }
//        }
//
//        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
//        public void applyData(Builder builder) {
//            for (PrefixedAddress address : mAddresses) {
//                builder.addAddress(address.mAddress, address.mPrefix);
//            }
//			/* add routes depending on whether split tunneling is allowed or not,
//			 * that is, whether we have to handle and block non-VPN traffic */
//                if (mIPv4Seen) {	/* split tunneling is used depending on the routes */
//                    for (PrefixedAddress route : mRoutesIPv4) {
//                        builder.addRoute(route.mAddress, route.mPrefix);
//                    }
//                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {	/* allow traffic that would otherwise be blocked to bypass the VPN */
//                    builder.allowFamily(OsConstants.AF_INET);
//                }
//			/* same thing for IPv6 */
//                if (mIPv6Seen) {
//                    for (PrefixedAddress route : mRoutesIPv6) {
//                        builder.addRoute(route.mAddress, route.mPrefix);
//                    }
//                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                    builder.allowFamily(OsConstants.AF_INET6);
//                }
//            builder.setMtu(mMtu);
//        }
//
//        private boolean isIPv6(String address) throws UnknownHostException {
//            InetAddress addr = InetAddress.getByName(address);
//            if (addr instanceof Inet4Address) {
//                return false;
//            } else if (addr instanceof Inet6Address) {
//                return true;
//            }
//            return false;
//        }
//
//        private class PrefixedAddress {
//            public String mAddress;
//            public int mPrefix;
//
//            public PrefixedAddress(String address, int prefix) {
//                this.mAddress = address;
//                this.mPrefix = prefix;
//            }
//        }
//    }
//
//    class VpnConnectJob implements Runnable {
//        private CharonVpnServicebak service;
//
//        public VpnConnectJob(CharonVpnServicebak service) {
//            this.service = service;
//        }
//
//        @Override
//        public void run() {
////            stopCurrentConnection();
//            BuilderAdapter builder = new BuilderAdapter(NetApi.getGateWay(), null);
//            if (initializeCharon(builder, mLogFile, false)) {
//                LogUtil.i("start charon");
//                SettingsWriter writer = new SettingsWriter();
//                writer.setValue("global.language", Locale.getDefault().getLanguage());
//                writer.setValue("global.mtu", NetApi.getMtu());
//                writer.setValue("connection.type", NetApi.getVpnType());
//                writer.setValue("connection.server", NetApi.getGateWay());
//                writer.setValue("connection.port", NetApi.getPort());
//                writer.setValue("connection.username", NetApi.getName());
//                writer.setValue("connection.password", NetApi.getPwd());
//                initiate(writer.serialize());
//            } else {
//                LogUtil.e("failed to start charon");
//                setError(ErrorState.GENERIC_ERROR);
//                setState(State.DISABLED);
//            }
//        }
//    }
//}
