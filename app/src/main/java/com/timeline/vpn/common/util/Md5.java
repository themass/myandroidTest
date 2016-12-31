/**
 *
 */
package com.timeline.vpn.common.util;

/**
 * @author Administrator
 */
public class Md5 {

    public static String encode(String src) {
        String dest = "";
        java.security.MessageDigest alga;
        try {
            alga = java.security.MessageDigest.getInstance("MD5");
            alga.update(src.getBytes());
            dest = byteHEX(alga.digest());
        } catch (Exception e) {
            LogUtil.e(e);
        }
        return dest;

    }

    private static String byteHEX(byte[] bytes) {
        char[] digit =
                {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        StringBuilder resultSb = new StringBuilder();
        for (byte ib : bytes) {
            char[] ob = new char[2];
            ob[0] = digit[(ib >>> 4) & 0X0F];
            ob[1] = digit[ib & 0X0F];
            resultSb.append(ob);
        }
        return resultSb.toString();
    }

}
