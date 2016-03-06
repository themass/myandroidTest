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

public enum Protocol {
    TCP((byte) 6, "tcp", "tcp6"),
    UDP((byte) 17, "udp", "udp6");

    private final byte mValue;
    private String[] mNames;

    private Protocol(byte value, String... names) {
        mValue = value;
        mNames = names;
    }

    /**
     * Get the protocol from the given protocol name, if found.
     *
     * @param name protocol name (e.g. "udp" or "tcp")
     * @return enum entry or null
     */
    public static com.timeline.vpn.service.imc.collectors.Protocol fromName(String name) {
        for (com.timeline.vpn.service.imc.collectors.Protocol protocol : com.timeline.vpn.service.imc.collectors.Protocol.values()) {
            for (String keyword : protocol.mNames) {
                if (keyword.equalsIgnoreCase(name)) {
                    return protocol;
                }
            }
        }
        return null;
    }

    /**
     * Get the numeric value of the protocol.
     *
     * @return numeric value
     */
    public byte getValue() {
        return mValue;
    }
}
