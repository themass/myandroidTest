package com.timeline.vpn.constant;


import com.timeline.vpn.service.utils.VpnType;

/**
 * Created by gqli on 2015/9/1.
 */
public class Constants {
    public static final int SELECT_TRUSTED_CERTIFICATE = 0;
    public static final String DEFAULT_REFERER = "http://timeline.vpn.com/";
    public static VpnType mVpnType = VpnType.IKEV2_CERT;
    public static boolean IS_DEBUG_OPEN = true;
    public static int connTimeOut = 20;
    public static boolean  USE_BYOD = false;
    public static VpnType vpnType = VpnType.IKEV2_EAP;
    public static final String BASE_HOST = "http://172.30.18.83:8080/test/api";//TODO 测试地址
    public static final String SERVERLIST_URL = BASE_HOST + "/server/list.json";


//    public static final String TAB_HOME = "TAB_HOME";
//    public static final String TAB_MY = "TAB_MY";
//    public static final String TAB_PHOTO = "TAB_PHOTO";
//    public static final String TAB_MSG = "TAB_MSG";
//    public static final String TAB_MORE = "TAB_MORE";
//
//    public static final String TAB_TECHNOLOGY = "TAB_TECHNOLOGY";
//    public static final String TAB_NEWS = "TAB_NEWS";
//    public static final String TAB_FUN = "TAB_FUN";
//    public static final String TAB_SPORT = "TAB_SPORT";

}
