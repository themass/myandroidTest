package com.timeline.vpn.common.util;

import android.os.Build;

/**
 * 版本匹配的问题
 *
 * @author Cyning
 * @date 2014-7-10 上午10:01:27
 * @description android设备的系统相关
 */
public class AndroidVersionUtils {
    public static boolean hasFroyo() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
    }

    public static boolean hasGingerbread() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
    }

    public static boolean hasHoneycomb() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    public static boolean hasHoneycombMR1() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1;
    }

    /**
     * api 14
     *
     * @return 4.0
     */
    public static boolean hasIceCreamSandwich() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }


    public static boolean hasJellyBean() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    public static boolean hasKitKat() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    /**
     * api 21
     *
     * @return 5.0
     */
    public static boolean hasLollipop() {
        //Build.VERSION_CODES.LOLLIPOP
        return Build.VERSION.SDK_INT >= 21;
    }

}
