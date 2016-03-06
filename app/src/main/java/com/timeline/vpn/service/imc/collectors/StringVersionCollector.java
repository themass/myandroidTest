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


import com.timeline.vpn.service.imc.attributes.Attribute;
import com.timeline.vpn.service.imc.attributes.StringVersionAttribute;

public class StringVersionCollector implements Collector {
    @Override
    public Attribute getMeasurement() {
        StringVersionAttribute attribute = new StringVersionAttribute();
        attribute.setProductVersionNumber(android.os.Build.VERSION.RELEASE);
        attribute.setInternalBuildNumber(android.os.Build.DISPLAY);
        return attribute;
    }
}
