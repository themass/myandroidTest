/*
 * Copyright (C) 2013 Tobias Brunner
 * Copyright (C) 2012 Christoph Buehler
 * Copyright (C) 2012 Patrick Loetscher
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

package com.timeline.vpn.service.imc.collectors;

import android.content.ContentResolver;
import android.content.Context;

import java.util.Locale;

public class SettingsCollector implements com.timeline.vpn.service.imc.collectors.Collector {
    private final ContentResolver mContentResolver;
    private final String[] mSettings;

    public SettingsCollector(Context context, String[] args) {
        mContentResolver = context.getContentResolver();
        mSettings = args;
    }

    @Override
    public com.timeline.vpn.service.imc.attributes.Attribute getMeasurement() {
        if (mSettings == null || mSettings.length == 0) {
            return null;
        }
        com.timeline.vpn.service.imc.attributes.SettingsAttribute attribute = new com.timeline.vpn.service.imc.attributes.SettingsAttribute();
        for (String name : mSettings) {
            String value = android.provider.Settings.Secure.getString(mContentResolver, name.toLowerCase(Locale.US));
            if (value == null) {
                value = android.provider.Settings.System.getString(mContentResolver, name.toLowerCase(Locale.US));
            }
            if (value != null) {
                attribute.addSetting(name, value);
            }
        }
        return attribute;
    }
}
