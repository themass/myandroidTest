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

package org.strongswan.android.logic.utils;


public class Utils {
    static final char[] HEXDIGITS = "0123456789abcdef".toCharArray();

    /**
     * 由 native 侧校验一条 IKE/ESP 提案串是否可被解析。
     * <p>
     * 对应 libandroidbridge 中的 Java_org_strongswan_android_utils_Utils_isProposalValid，
     * 内部走 proposal_create_from_string()，因此与真正的协商路径使用同一套语法规则。
     * 用它可以在运行时拦住"写了但解析失败、静默回退默认算法"这类问题。
     *
     * @param ike    是否为 IKE 提案（否则按 ESP 校验）
     * @param proposal 单条提案串，'-' 分隔算法
     * @return true 表示可解析
     */
    public static native boolean isProposalValid(boolean ike, String proposal);

    /**
     * Converts the given byte array to a hexadecimal string encoding.
     *
     * @param bytes byte array to convert
     * @return hex string
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i];
            hex[i * 2] = HEXDIGITS[(value & 0xf0) >> 4];
            hex[i * 2 + 1] = HEXDIGITS[value & 0x0f];
        }
        return new String(hex);
    }
}
