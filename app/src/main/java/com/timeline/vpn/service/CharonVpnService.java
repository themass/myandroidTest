/*
 * Copyright (C) 2012-2013 Tobias Brunner
 * Copyright (C) 2012 Giuliano Grassi
 * Copyright (C) 2012 Ralf Sager
 * Hochschule fuer Technik Rapperswil
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

package com.timeline.vpn.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.util.Log;

import com.timeline.vpn.bean.vo.VpnProfile;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.service.utils.SettingsWriter;
import com.timeline.vpn.service.utils.VpnType;
import com.timeline.vpn.ui.main.MainFragment;

import java.io.File;
import java.io.StringBufferInputStream;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CharonVpnService extends VpnService {
    public static final String LOG_FILE = "charon.log";
    public static final String PROFILE = "PROFILE";
    public static final String COMMAND = "COMMAND";
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

    /*
     * The libraries are extracted to /data/data/org.strongswan.android/...
     * during installation.
     */
    static {
        System.loadLibrary("strongswan");
        if (Constants.USE_BYOD) {
            System.loadLibrary("tncif");
            System.loadLibrary("tnccs");
            System.loadLibrary("imcv");
        }
        System.loadLibrary("hydra");
        System.loadLibrary("charon");
        System.loadLibrary("ipsec");
        System.loadLibrary("androidbridge");
    }

    private final Object mServiceLock = new Object();
    private final IBinder mBinder = new LocalBinder();
    private final List<VpnStateListener> mListeners = new ArrayList<VpnStateListener>();
    private String mLogFile;
    private VpnProfile mCurrentProfile;
    private HandlerThread mWorkThread;
    private Handler mWorkHandler;
    private State mCurrentState = State.DISABLED;
    private boolean mIsDisconnecting = false;
    public static String getLogFilePath(Context context){
        return context.getFilesDir().getAbsolutePath() + File.separator + LOG_FILE;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            int command = bundle.getInt(COMMAND, -1);
            switch (command) {
                case Command.START:
                    startConnection((VpnProfile) bundle.getSerializable(PROFILE));
                    break;
                case Command.STOP:
                    stopCurrentConnection();
                    break;
                default:
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        mLogFile =getLogFilePath(this);
//        mLogFile = Environment.getExternalStorageDirectory() + File.separator + LOG_FILE;
        mWorkThread = new HandlerThread(WORK_ANME);
        mWorkThread.start();
        mWorkHandler = new Handler(mWorkThread.getLooper());
    }

    @Override
    public void onRevoke() {
        stopCurrentConnection();
    }

    @Override
    public void onDestroy() {
        mWorkThread.quit();
    }

    /**
     * Stop any existing connection by deinitializing charon.
     */
    public void stopCurrentConnection() {
        if (mCurrentProfile != null) {
            LogUtil.i("startConnection " + mCurrentProfile.getName());
            mCurrentProfile = null;
            mWorkHandler.post(new DisConnectJob());
        }
    }

    /**
     * Notify the state service about a new connection attempt.
     * Called by the handler thread.
     *
     * @param profile currently active VPN profile
     */
    public void startConnection(VpnProfile profile) {
        LogUtil.i("startConnection " + profile.getName());
        mCurrentProfile = profile;
        mWorkHandler.post(new ConnectJob());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void setState(State state, ErrorState errorState) {
        mCurrentState = state;
        for (VpnStateListener listener : mListeners) {
            listener.stateChanged(state, errorState);
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
                    setState(State.CONNECTING, ErrorState.NO_ERROR);
                }
                break;
            case STATE_CHILD_SA_UP:
                setState(State.CONNECTED, ErrorState.NO_ERROR);
                break;
            case STATE_AUTH_ERROR:
                setState(State.DISABLED, ErrorState.AUTH_FAILED);
                break;
            case STATE_PEER_AUTH_ERROR:
                setState(State.DISABLED, ErrorState.PEER_AUTH_FAILED);
                break;
            case STATE_LOOKUP_ERROR:
                setState(State.DISABLED, ErrorState.LOOKUP_FAILED);
                break;
            case STATE_UNREACHABLE_ERROR:
                setState(State.DISABLED, ErrorState.UNREACHABLE);
                break;
            case STATE_GENERIC_ERROR:
                setState(State.DISABLED, ErrorState.GENERIC_ERROR);
                break;
            default:
                Log.e(TAG, "Unknown status code received");
                setState(State.DISABLED, ErrorState.UNKONE_ERROR);
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
    }

    /**
     * Add a remediation instruction to the VPN state service.
     * Called via JNI by different threads (but not concurrently).
     *
     * @param xml XML text
     */
    public void addRemediationInstruction(String xml) {
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
            Certificate localCertificate = localCertificateFactory.generateCertificate(new StringBufferInputStream(mCurrentProfile.getCert()));
            localArrayList.add(localCertificate.getEncoded());
            return (byte[][]) localArrayList.toArray(new byte[localArrayList.size()][]);
        } catch (Exception e) {
            LogUtil.e(e);
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
        ArrayList<byte[]> encodings = new ArrayList<byte[]>();
        X509Certificate[] chain = KeyChain.getCertificateChain(getApplicationContext(), mCurrentProfile.getUserCertificateAlias());
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
        return KeyChain.getPrivateKey(getApplicationContext(), mCurrentProfile.getUserCertificateAlias());

    }

    /**
     * Initialization of charon, provided by libandroidbridge.so
     *
     * @param builder BuilderAdapter for this connection
     * @param logfile absolute path to the logfile
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

    public void registerListener(VpnStateListener listener) {
        mListeners.add(listener);
    }

    public State getCurrentVpnState() {
        return mCurrentState;
    }

    /**
     * Unregister a listener from this Service.
     *
     * @param listener listener to unregister
     */
    public void unregisterListener(VpnStateListener listener) {
        mListeners.remove(listener);
    }

    public enum State {
        DISABLED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
    }

    public enum ErrorState {
        NO_ERROR,
        AUTH_FAILED,
        PEER_AUTH_FAILED,
        LOOKUP_FAILED,
        UNREACHABLE,
        GENERIC_ERROR,
        UNKONE_ERROR,
    }

    public interface VpnStateListener {
        public void stateChanged(State state, ErrorState errorState);
    }

    public class Command {
        public static final int START = 1;
        public static final int STOP = 2;
    }

    public class LocalBinder extends Binder {
        public CharonVpnService getService() {
            return CharonVpnService.this;
        }
    }

    public class DisConnectJob implements Runnable {
        @Override
        public void run() {
            LogUtil.i("charon stopped  mCurrentState=" + mCurrentState + "  thread=" + Thread.currentThread().getName());
            if (mCurrentState == State.CONNECTED) {
                mIsDisconnecting = true;
                mCurrentState = State.DISABLED;
                deinitializeCharon();
            }
        }
    }

    public class ConnectJob implements Runnable {
        @Override
        public void run() {
            Log.i(TAG, "charon started mCurrentState=" + mCurrentState + "  thread=" + Thread.currentThread().getName());
            if (mCurrentState == State.DISABLED && mCurrentProfile != null) {
                mCurrentState = State.CONNECTING;
                BuilderAdapter builder = new BuilderAdapter(mCurrentProfile.getName());
                mIsDisconnecting = false;
                if (initializeCharon(builder, mLogFile, mCurrentProfile.getVpnType().has(VpnType.VpnTypeFeature.BYOD))) {
                    SettingsWriter writer = new SettingsWriter();
                    writer.setValue("global.language", Locale.getDefault().getLanguage());
                    writer.setValue("global.mtu", mCurrentProfile.getMTU());
                    writer.setValue("connection.type", mCurrentProfile.getVpnType().getIdentifier());
                    writer.setValue("connection.server", mCurrentProfile.getGateway());
                    writer.setValue("connection.port", mCurrentProfile.getPort());
                    writer.setValue("connection.username", "123");
                    writer.setValue("connection.password", mCurrentProfile.getPassword());
                    initiate(writer.serialize());
                } else {
                    Log.e(TAG, "failed to start charon");
                    mCurrentProfile = null;
                    mCurrentState = State.DISABLED;
                    setState(State.DISABLED, ErrorState.GENERIC_ERROR);

                }
            }
        }
    }

    /**
     * Adapter for VpnService.Builder which is used to access it safely via JNI.
     * There is a corresponding C object to access it from native code.
     */
    public class BuilderAdapter {
        private final String mName;
        private VpnService.Builder mBuilder;
        private BuilderCache mCache;
        private BuilderCache mEstablishedCache;

        public BuilderAdapter(String name) {
            mName = name;
            mBuilder = createBuilder(name);
            mCache = new BuilderCache();
        }

        private VpnService.Builder createBuilder(String name) {
            VpnService.Builder builder = new CharonVpnService.Builder();
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
                mBuilder.addAddress(address, prefixLength);
                mCache.addAddress(address, prefixLength);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean addDnsServer(String address) {
            try {
                mBuilder.addDnsServer(address);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean addRoute(String address, int prefixLength) {
            try {
                mBuilder.addRoute(address, prefixLength);
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
                mBuilder.setMtu(mtu);
                mCache.setMtu(mtu);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized int establish() {
            ParcelFileDescriptor fd;
            try {
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
            mCache = new BuilderCache();
            return fd.detachFd();
        }

        public synchronized int establishNoDns() {
            ParcelFileDescriptor fd;

            if (mEstablishedCache == null) {
                return -1;
            }
            try {
                VpnService.Builder builder = createBuilder(mName);
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
        private final List<PrefixedAddress> mRoutes = new ArrayList<PrefixedAddress>();
        private int mMtu;

        public void addAddress(String address, int prefixLength) {
            mAddresses.add(new PrefixedAddress(address, prefixLength));
        }

        public void addRoute(String address, int prefixLength) {
            mRoutes.add(new PrefixedAddress(address, prefixLength));
        }

        public void setMtu(int mtu) {
            mMtu = mtu;
        }

        public void applyData(VpnService.Builder builder) {
            for (PrefixedAddress address : mAddresses) {
                builder.addAddress(address.mAddress, address.mPrefix);
            }
            for (PrefixedAddress route : mRoutes) {
                builder.addRoute(route.mAddress, route.mPrefix);
            }
            builder.setMtu(mMtu);
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
