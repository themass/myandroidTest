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

package com.timeline.vpn.service.imc;

import android.content.Context;

import com.timeline.vpn.service.imc.attributes.Attribute;


public class AndroidImc {
    private final Context mContext;

    public AndroidImc(Context context) {
        mContext = context;
    }

    /**
     * Get a measurement (the binary encoding of the requested attribute) for
     * the given vendor specific attribute type.
     *
     * @param vendor vendor ID
     * @param type   vendor specific attribute type
     * @return encoded attribute, or null if not available or failed
     */
    public byte[] getMeasurement(int vendor, int type) {
        return getMeasurement(vendor, type, null);
    }

    /**
     * Get a measurement (the binary encoding of the requested attribute) for
     * the given vendor specific attribute type.
     *
     * @param vendor vendor ID
     * @param type   vendor specific attribute type
     * @param args   optional arguments for a measurement
     * @return encoded attribute, or null if not available or failed
     */
    public byte[] getMeasurement(int vendor, int type, String[] args) {
        com.timeline.vpn.service.imc.attributes.AttributeType attributeType = com.timeline.vpn.service.imc.attributes.AttributeType.fromValues(vendor, type);
        com.timeline.vpn.service.imc.collectors.Collector collector = null;

        switch (attributeType) {
            case IETF_PRODUCT_INFORMATION:
                collector = new com.timeline.vpn.service.imc.collectors.ProductInformationCollector();
                break;
            case IETF_STRING_VERSION:
                collector = new com.timeline.vpn.service.imc.collectors.StringVersionCollector();
                break;
            case IETF_PORT_FILTER:
                collector = new com.timeline.vpn.service.imc.collectors.PortFilterCollector();
                break;
            case IETF_INSTALLED_PACKAGES:
                collector = new com.timeline.vpn.service.imc.collectors.InstalledPackagesCollector(mContext);
                break;
            case ITA_SETTINGS:
                collector = new com.timeline.vpn.service.imc.collectors.SettingsCollector(mContext, args);
                break;
            case ITA_DEVICE_ID:
                collector = new com.timeline.vpn.service.imc.collectors.DeviceIdCollector(mContext);
                break;
            default:
                break;
        }
        if (collector != null) {
            Attribute attribute = collector.getMeasurement();
            if (attribute != null) {
                return attribute.getEncoding();
            }
        }
        return null;
    }
}
