/*
 * Copyright (C) 2013 Tobias Brunner
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


public class DeviceIdCollector implements com.timeline.vpn.service.imc.collectors.Collector {
    private final ContentResolver mContentResolver;

    public DeviceIdCollector(Context context) {
        mContentResolver = context.getContentResolver();
    }

    @Override
    public com.timeline.vpn.service.imc.attributes.Attribute getMeasurement() {
        String id = android.provider.Settings.Secure.getString(mContentResolver, "android_id");
        if (id != null) {
            com.timeline.vpn.service.imc.attributes.DeviceIdAttribute attribute = new com.timeline.vpn.service.imc.attributes.DeviceIdAttribute();
            attribute.setDeviceId(id);
            return attribute;
        }
        return null;
    }
}
