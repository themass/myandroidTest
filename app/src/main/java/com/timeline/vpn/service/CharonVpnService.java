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
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.util.Log;

import com.timeline.vpn.service.imc.ImcState;
import com.timeline.vpn.service.imc.RemediationInstruction;

import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.data.VpnProfileDataSource;
import org.strongswan.android.data.VpnType.VpnTypeFeature;
import org.strongswan.android.ui.VpnManagerActivity;

import java.io.File;
import java.io.StringBufferInputStream;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class CharonVpnService extends VpnService implements Runnable {
    public static final String LOG_FILE = "charon.log";
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

    /*
     * The libraries are extracted to /data/data/org.strongswan.android/...
     * during installation.
     */
    static {
        System.loadLibrary("strongswan");

        if (VpnManagerActivity.USE_BYOD) {
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
    private String mLogFile;
    private VpnProfileDataSource mDataSource;
    private Thread mConnectionHandler;
    private VpnProfile mCurrentProfile;
    private volatile String mCurrentCertificateAlias;
    private volatile String mCurrentUserCertificateAlias;
    private VpnProfile mNextProfile;
    private volatile boolean mProfileUpdated;
    private volatile boolean mTerminate;
    private volatile boolean mIsDisconnecting;
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
            /* we are now ready to start the handler thread */
            mConnectionHandler.start();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Bundle bundle = intent.getExtras();
            VpnProfile profile = null;
            if (bundle != null) {
                profile = mDataSource.getVpnProfile(bundle.getLong(VpnProfileDataSource.KEY_ID));
                if (profile != null) {
                    String password = bundle.getString(VpnProfileDataSource.KEY_PASSWORD);
                    profile.setPassword(password);
                }
            }
            setNextProfile(profile);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        mLogFile = getFilesDir().getAbsolutePath() + File.separator + LOG_FILE;

        mDataSource = new VpnProfileDataSource(this);
        mDataSource.open();
		/* use a separate thread as main thread for charon */
        mConnectionHandler = new Thread(this);
		/* the thread is started when the service is bound */
        bindService(new Intent(this, VpnStateService.class),
                mServiceConnection, Service.BIND_AUTO_CREATE);
    }

    @Override
    public void onRevoke() {	/* the system revoked the rights grated with the initial prepare() call.
		 * called when the user clicks disconnect in the system's VPN dialog */
        setNextProfile(null);
    }

    @Override
    public void onDestroy() {
        mTerminate = true;
        setNextProfile(null);
        try {
            mConnectionHandler.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (mService != null) {
            unbindService(mServiceConnection);
        }
        mDataSource.close();
    }

    /**
     * Set the profile that is to be initiated next. Notify the handler thread.
     *
     * @param profile the profile to initiate
     */
    private void setNextProfile(VpnProfile profile) {
        synchronized (this) {
            this.mNextProfile = profile;
            mProfileUpdated = true;
            notifyAll();
        }
    }

    @Override
    public void run() {
        while (true) {
            synchronized (this) {
                try {
                    while (!mProfileUpdated) {
                        wait();
                    }

                    mProfileUpdated = false;
                    stopCurrentConnection();
                    if (mNextProfile == null) {
                        setState(VpnStateService.State.DISABLED);
                        if (mTerminate) {
                            break;
                        }
                    } else {
                        mCurrentProfile = mNextProfile;
                        mNextProfile = null;

						/* store this in a separate (volatile) variable to avoid
						 * a possible deadlock during deinitialization */
                        mCurrentCertificateAlias = mCurrentProfile.getCertificateAlias();
                        mCurrentUserCertificateAlias = mCurrentProfile.getUserCertificateAlias();

                        startConnection(mCurrentProfile);
                        mIsDisconnecting = false;

                        BuilderAdapter builder = new BuilderAdapter(mCurrentProfile.getName());
                        if (initializeCharon(builder, mLogFile, mCurrentProfile.getVpnType().has(VpnTypeFeature.BYOD))) {
                            Log.i(TAG, "charon started");
                            initiate(mCurrentProfile.getVpnType().getIdentifier(),
                                    mCurrentProfile.getGateway(), "123",
                                    "123456");
                        } else {
                            Log.e(TAG, "failed to start charon");
                            setError(VpnStateService.ErrorState.GENERIC_ERROR);
                            setState(VpnStateService.State.DISABLED);
                            mCurrentProfile = null;
                        }
                    }
                } catch (InterruptedException ex) {
                    stopCurrentConnection();
                    setState(VpnStateService.State.DISABLED);
                }
            }
        }
    }

    /**
     * Stop any existing connection by deinitializing charon.
     */
    private void stopCurrentConnection() {
        synchronized (this) {
            if (mCurrentProfile != null) {
                setState(VpnStateService.State.DISCONNECTING);
                mIsDisconnecting = true;
                deinitializeCharon();
                Log.i(TAG, "charon stopped");
                mCurrentProfile = null;
            }
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
//        ArrayList<byte[]> certs = new ArrayList<byte[]>();
//        TrustedCertificateManager certman = TrustedCertificateManager.getInstance();
//        try {
//            String alias = this.mCurrentCertificateAlias;
//            if (alias != null) {
//                X509Certificate cert = certman.getCACertificateFromAlias(alias);
//                if (cert == null) {
//                    return null;
//                }
//                certs.add(cert.getEncoded());
//            } else {
//                Collection<X509Certificate> caList = certman.getAllCACertificates().values();
//                for (X509Certificate cert : caList) {
//                    certs.add(cert.getEncoded());
//                }
//            }
//        } catch (CertificateEncodingException e) {
//            e.printStackTrace();
//            return null;
//        }
//        return certs.toArray(new byte[certs.size()][]);

        ArrayList localArrayList = new ArrayList();
        try{
        String ca = "-----BEGIN CERTIFICATE-----\n" +
                "MIIDMjCCAhqgAwIBAgIIKB6/xEUnNqcwDQYJKoZIhvcNAQEFBQAwNzELMAkGA1UE\n" +
                "BhMCQ04xETAPBgNVBAoTCHRpbWVsaW5lMRUwEwYDVQQDEww0Ny44OC44NS4xNzIw\n" +
                "HhcNMTYwMjI5MTQ1MTAyWhcNMTkwMjI4MTQ1MTAyWjA3MQswCQYDVQQGEwJDTjER\n" +
                "MA8GA1UEChMIdGltZWxpbmUxFTATBgNVBAMTDDQ3Ljg4Ljg1LjE3MjCCASIwDQYJ\n" +
                "KoZIhvcNAQEBBQADggEPADCCAQoCggEBAN8hXVt8OzQSbOuXyxYz/+pKspFuiNKb\n" +
                "4y1nnOAILRK1htzCcU6KcpMd4rgj0lvYlKO+BP44u9Ymt6dG/WvlejqiVH6jUF+C\n" +
                "x4RFYVsEDFXc3cQNe+/nJPQrPDlbXCHjticDPZlu2CDwJc9E8EaLKHxdS7mOrOCS\n" +
                "16Y1h5iv4TMdZgufwKSvs8WzIuysIBVWAy7phSw89BnBJ7y9rak1jtf6zY193Cs/\n" +
                "aA9C4GEuv0W9lJ3Yujm0psnHaGzc7vH/9wSRxAyPr1EzzsfXNT2mZ+4xe15Fh4vC\n" +
                "4KfcFFgdMkjtdfq+6wPKtex46FCxBA2b7JyFdAYQjpoFR+lbTGM6mLECAwEAAaNC\n" +
                "MEAwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFK2X\n" +
                "geXqBRGGT+7pz09Cdp77rJ1kMA0GCSqGSIb3DQEBBQUAA4IBAQBSKEXSBM6TJOYx\n" +
                "6B86n9l6y1hX2yPKHCXqmUZjry6DC3yF5Aq+RxWftQ8V/tBfrjZUVegls+BQ8O6q\n" +
                "jph1Ewf/Z2v/JTgm3fubcPW+en403WblHPXMDY21YTFrDZy0K/lm3e+4nHL6L3lS\n" +
                "yBhJPG/m3QBW4wC6wAXRO0cSnbPEwSw/LRyGmsfrTwTma6kDz6CbblIgBQHH8JGA\n" +
                "YLvc6TT5TeBkRbIqLFsLv6JWiwlfuXEISsTe58OM2FxMS8duXE0bIpYFdw/CW3Rk\n" +
                "kI5IcU45DJVeQTCwc9AOvigbwNZuZ36RgutCcg/vj4rvpIMZaeesDhmcbARNArUQ\n" +
                "rPaoNOhZ\n" +
                "-----END CERTIFICATE-----";
        CertificateFactory localCertificateFactory = CertificateFactory.getInstance("X.509");
        Certificate localCertificate = localCertificateFactory.generateCertificate(new StringBufferInputStream(ca));
        localArrayList.add(localCertificate.getEncoded());
        return (byte[][]) localArrayList.toArray(new byte[localArrayList.size()][]);
    }catch (Exception localException)
    {
        localException.printStackTrace();
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
        X509Certificate[] chain = KeyChain.getCertificateChain(getApplicationContext(), mCurrentUserCertificateAlias);
        if (chain == null || chain.length == 0) {
            return null;
        }
        for (X509Certificate cert : chain) {
            encodings.add(cert.getEncoded());
        }
        return encodings.toArray(new byte[encodings.size()][]);
//        ArrayList localArrayList = new ArrayList();
//        try {
//            String ca = "-----BEGIN CERTIFICATE-----\n" +
//                    "MIIDEzCCAfugAwIBAgIIVXGDpTq/txUwDQYJKoZIhvcNAQEFBQAwNzELMAkGA1UE\n" +
//                    "BhMCQ04xETAPBgNVBAoTCHRpbWVsaW5lMRUwEwYDVQQDEww0Ny44OC44NS4xNzIw\n" +
//                    "HhcNMTYwMjI5MTQ1MTAyWhcNMTkwMjI4MTQ1MTAyWjA3MQswCQYDVQQGEwJDTjER\n" +
//                    "MA8GA1UEChMIdGltZWxpbmUxFTATBgNVBAMTDDQ3Ljg4Ljg1LjE3MjCCASIwDQYJ\n" +
//                    "KoZIhvcNAQEBBQADggEPADCCAQoCggEBANH9ClGBJm0+auy9sY8vbsYecTlIAfut\n" +
//                    "wzqF2Y0+PCAhrf5M+cz8MdPOffrTJ0xAcMYv7L0nr4nOn0U087o9PAkfukim/SfF\n" +
//                    "Wfm+HO6QSObQVCjQY8hZ1xUQksYe6zgcQnxxLzAVYje9Wt1FsQppb8XpxInjbv7e\n" +
//                    "WZvmH3VItuf6A9GbomlNT1N0Rs03h3ZsOv/kIKTYAZqfVu/Z2gqyJukvEVraB8OV\n" +
//                    "dq7yY1Xl4D1j1tLG1xl/7Cu/gX4AdR58j+aKgDahMja9A9u1t4FBP7MKCWd+yIr0\n" +
//                    "n8fG0+gRiu9zKmZWygui2YrYDV3Fy0+/Y2pbt0wO3ZNkYHh8svt/rB8CAwEAAaMj\n" +
//                    "MCEwHwYDVR0jBBgwFoAUrZeB5eoFEYZP7unPT0J2nvusnWQwDQYJKoZIhvcNAQEF\n" +
//                    "BQADggEBAG+w5CZgzmQTm6siao+72jSZ9UzTgmUyHtt6gxK+IlpmPU/tBDcBqG5Q\n" +
//                    "djtSSSsWVfxCX7x6brxH4mS55htPAjLLCbxXt0MF1yWClwQw8Gifa/U6i74GYLF/\n" +
//                    "olAq1Cb45b897+rnsQhQBYre6WhGpO1DnjCRoHuX87nA2ACMmU4+UWkrMpdkpVJB\n" +
//                    "3yEJloAStPkejRlIflgcWtWVXhEFKz870YeLxl3419b5MOGE6I0QaDuLYlZlQu5D\n" +
//                    "9IaUB9Iu8zw9mDnjlQLgd5hfmfFH7LmKgHrMJU/jW0Z4FjdaIF/FcUXO3AiGFWcv\n" +
//                    "rX7t5hBOt5M7tzbpXzgqeWsAw9VJrBk=\n" +
//                    "-----END CERTIFICATE-----";
//            CertificateFactory localCertificateFactory = CertificateFactory.getInstance("X.509");
//            Certificate localCertificate = localCertificateFactory.generateCertificate(new StringBufferInputStream(ca));
//            localArrayList.add(localCertificate.getEncoded());
//
//            return (byte[][]) localArrayList.toArray(new byte[localArrayList.size()][]);
//        }catch (Exception localException)
//        {
//            localException.printStackTrace();
//            return null;
//        }
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
        PrivateKey key = KeyChain.getPrivateKey(getApplicationContext(), mCurrentUserCertificateAlias);
        return KeyChain.getPrivateKey(getApplicationContext(), mCurrentUserCertificateAlias);
//        String key = "MIIEowIBAAKCAQEA3yFdW3w7NBJs65fLFjP/6kqykW6I0pvjLWec4AgtErWG3MJx\n" +
//                "Topykx3iuCPSW9iUo74E/ji71ia3p0b9a+V6OqJUfqNQX4LHhEVhWwQMVdzdxA17\n" +
//                "7+ck9Cs8OVtcIeO2JwM9mW7YIPAlz0TwRosofF1LuY6s4JLXpjWHmK/hMx1mC5/A\n" +
//                "pK+zxbMi7KwgFVYDLumFLDz0GcEnvL2tqTWO1/rNjX3cKz9oD0LgYS6/Rb2Undi6\n" +
//                "ObSmycdobNzu8f/3BJHEDI+vUTPOx9c1PaZn7jF7XkWHi8Lgp9wUWB0ySO11+r7r\n" +
//                "A8q17HjoULEEDZvsnIV0BhCOmgVH6VtMYzqYsQIDAQABAoIBAQDXZD3asnH0MlUy\n" +
//                "l7VUGZ5RbQozmaA6roD+s6acHUhlZxZoxMrcJomzuoXBe9L1Fn1mE2h+gAXTFEz1\n" +
//                "DXJVVZBISauHE9/iUzyGUvNICQBfYnYxa1LYoyjpC1N3itnFXSvZPSeZlYbNRQU6\n" +
//                "sDHB51ONugH/fdskbpv9tm7O1flufIUJHVAEePRPN1O0abD3mDpooG7ook+qXzQb\n" +
//                "cmv5gXrm2MRf+oFSRFBXzEbWCaFeTXJ8q2RV05a8S/bnqUa/GOYB+Ef3rzbthbZA\n" +
//                "Fpp7cPqGfgP9bDpPjre+5TA3jEY8XtVqdeii8H/TuuZizPtlnBOefUNX8dL3MLH4\n" +
//                "ldXGYdqBAoGBAPXHr0xo87JEihGydvjOZr6NvdUIZF5wMiJv9Oo29ok0e36OASQA\n" +
//                "dWYalxyz9qP8D5g/Y0ohyFex9PDnjR2FnUAMcAO9ynDaXcqui0wDXUiIMSdCPn4z\n" +
//                "9KUX4DXnqQAO6c4a+nQ/Y4CxCa92v3YSj9vz8Mhi+5YnIYIQ8eJrsMLjAoGBAOho\n" +
//                "kz/pmhvjIvgwP1x97ijJNCObjcVkexXlbOz2yeGbY66XH5yIxoA5LzOLvo0Tn2pD\n" +
//                "qb9V+yY9XPFKZLyr1jLIGSoqSky4oUx1syLG/nKxyFjKH+6OSLMnwe0Vsjr/NKkA\n" +
//                "e3v5x8uucTAvU/lAfOCj2c9rcgdu3FrZi6uiHAZbAoGAW4hFYaiIcXCTFuom4pxD\n" +
//                "qV2yP99DOeSA11Bonr1fbVOtVkFebWD6V885Tcg753BdAm9A8//G09oDhaBGbqMv\n" +
//                "IAZBxTLubL72RSBh4ioymGuYGq3SBkAlMULGDm07BDmJm2dytM5SqT/FgqIvxbI9\n" +
//                "/0VG++nZFl92XDEhVBiVGM8CgYAbvQ+GG02Hco6j7K5DTcx1Fb6hWVmMa7e9H0rm\n" +
//                "5XWm9bLqDaN6PWDjwO9/SXiOwf7O4hCKg6qcH8rOCcEIO8IvAW7vd2iyy36E769X\n" +
//                "wURxUALHhihVgqnuqBofP+2r2PXiDeqvG1rbVpCv3ZY6M5Y0AZpnhT1w4axvM3PW\n" +
//                "Il0R7QKBgDkU1Q2WxOq9cPundwgwGTHTeVqPd/xUW1ME0ezROv8Iwf8611GggScL\n" +
//                "92fJIS7oNQgSg9ROKYAe3IF80PhftNuYlt2zvMo7UtzSvs8EaewRwurQK5wkXBAx\n" +
//                "uqBcZHbSa4WGQ+BbWlmpBWrm56MF4z/lj1EOGKpg4p+2/IQ46XNZ";
//        try {
//            byte[] keyBytes = Base64.decodeBase64(key.getBytes());
//            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
//            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
//            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
//            return privateKey;
//        }catch (Exception e){
//            e.printStackTrace();
//            return null;
//        }
    }

    public native boolean initializeCharon(BuilderAdapter builder, String logfile, boolean byod);

    /**
     * Deinitialize charon, provided by libandroidbridge.so
     */
    public native void deinitializeCharon();

    /**
     * Initiate VPN, provided by libandroidbridge.so
     */
    public native void initiate(String type, String gateway, String username, String password);

    /**
     * Adapter for VpnService.Builder which is used to access it safely via JNI.
     * There is a corresponding C object to access it from native code.
     */
    public class BuilderAdapter {
        private final String mName;
        private Builder mBuilder;
        private BuilderCache mCache;
        private BuilderCache mEstablishedCache;

        public BuilderAdapter(String name) {
            mName = name;
            mBuilder = createBuilder(name);
            mCache = new BuilderCache();
        }

        private Builder createBuilder(String name) {
            Builder builder = new Builder();
            builder.setSession(mName);

			/* even though the option displayed in the system dialog says "Configure"
			 * we just use our main Activity */
            Context context = getApplicationContext();
            Intent intent = new Intent(context, VpnManagerActivity.class);
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

        public void applyData(Builder builder) {
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
