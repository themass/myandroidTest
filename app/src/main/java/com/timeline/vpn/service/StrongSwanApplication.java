/*
 * Copyright (C) 2014 Tobias Brunner
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

import android.app.Application;
import android.content.Context;

import org.strongswan.android.security.LocalCertificateKeyStoreProvider;

import java.security.Security;

public class StrongSwanApplication extends Application {
    private static Context mContext;

    static {
        Security.addProvider(new LocalCertificateKeyStoreProvider());
    }

    /**
     * Returns the current application context
     *
     * @return context
     */
    public static Context getContext() {
        return StrongSwanApplication.mContext;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        StrongSwanApplication.mContext = getApplicationContext();
    }
}
