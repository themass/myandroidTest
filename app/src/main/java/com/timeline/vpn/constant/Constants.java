package com.timeline.vpn.constant;


import com.timeline.vpn.service.utils.VpnType;

/**
 * Created by gqli on 2015/9/1.
 */
public class Constants {
    public static final String FIRSTRUN = "isFirstRun";
    public static final int SELECT_TRUSTED_CERTIFICATE = 0;
    public static final String DEFAULT_REFERER = "http://timeline.vpn.com/";
//    public static final String BASE_HOST = "http://10.33.71.3:8080/vpn/api";//TODO 测试地址
            public static final String BASE_HOST = "http://192.168.1.6:8080/vpn/api";//TODO 测试地址
    public static final String API_SERVERLIST_URL = BASE_HOST + "/host/server/list.json?location=%s";
    public static final String API_FAB_ADSCLICK_URL = BASE_HOST + "/user/ads/score.json?score=%s";
    public static final String API_LOCATION_URL = BASE_HOST + "/host/server/location.json";
    public static final String API_LOGIN_URL = BASE_HOST + "/user/login.json";
    public static final String API_REG_URL = BASE_HOST + "/user/reg.json";
    public static final String API_USER_INFO_URL = BASE_HOST + "/user/info.json";
    public static final String API_IWANNA_URL = BASE_HOST + "/data/feed/wanna.json?start=%s&limit=20";
    public static final String API_IWANNA_LIKE_URL = BASE_HOST + "/data/feed/wanna/%s.json";
    public static final String API_VERSION_URL = BASE_HOST + "/data/version.json";
    public static final String API_RECOMMEND_URL = BASE_HOST + "/data/recommend.json?start=%s&limit=20";
    public static final String API_LOGOUT_URL = BASE_HOST + "/user/logout.json";

    public static final String URL = "url";
    public static final int STARTUP_SHOW_TIME_1500 = 1500; //启动页广告时长
    public static final int STARTUP_SHOW_TIME_3500 = 3500; //启动页广告时长
    public static final int STARTUP_SHOW_TIME_8000 = 8000; //启动页广告时长
    public static final int BANNER_ADS_GONE_LONG = 80000; //广告显示时长
    public static final int BANNER_ADS_GONE_SHORT = 5000; //启动页广告时长
    public static final int VIP_SHIMMER_DURATION = 2300;
    public static final int RECOMMAND_SHIMMER_DURATION = 1500;
    public static final int ADS_NO_MSG = 0;
    public static final int ADS_CLICK_MSG = 1;
    public static final int ADS_DISMISS_MSG = 2;
    public static final int ADS_PRESENT_MSG = 3;
    public static final int ADS_READY_MSG = 4;

    public static final int ADS_TYPE_BANNER = 0;
    public static final int ADS_TYPE_SPREAD = 1;
    public static final int ADS_TYPE_INTERSTITIAL = 2;
    public static final int ADS_TYPE_NATIVE = 3;
    public static final int UM_INTERVAL = 40;
    public static final int NO_VALUE_FLAG = -999;//无
    public static final int SUNNY = 0;//晴
    public static final int THUNDERSTORM = 2;//雷雨
    public static final int DRIZZLE = 3;//下蒙蒙细雨
    public static final int RAIN = 5;//雨
    public static final int SNOW = 6;//雪
    public static final int ATMOSPHERE = 7;//空气
    public static final int CLEAR = 800;//晴朗
    public static final int CLOUDS = 8;//云
    public static final int EXTREME = 9;//极端

    public static final String WEATHER_KEY = "weather";
    //location
    public static final String LOCATION_CHOOSE = "LOCATION_CHOOSE";
    public static final int LOCATION_TYPE_FREE = 0;
    public static final int LOCATION_TYPE_VIP = 1;
    public static final int LOCATION_TYPE_ADVIP = 2;
    public static final String[] sort_type = new String[]{"type_asc", "type_desc"};
    public static final String[] sort_country = new String[]{"ename_asc", "ename_desc"};
    public static final String[] sort_fea = new String[]{"level_asc", "level_desc"};
    public static final String sortType = "type";
    public static final String sortCountry = "ename";
    public static final String sortFea = "level";
    public static final String sortASC = "asc";
    public static final String sortDESC = "desc";
    //user
    public static final String LOGIN_USER = "LOGIN_USER";
    public static final String LOGIN_USER_LAST = "LOGIN_USER_LAST";
    public static final String DEVID = "Devid";
    public static final String HTTP_TOKEN_KEY = "Vpn-Token";
    public static final String HTTP_LANG = "lang";
    public static final String SEX_M = "M";
    public static final String SEX_F = "F";
    public static final int HTTP_SUCCESS = 0;
    public static final int HTTP_SUCCESS_CLEAR = 1;
    // 有新版本时，是否打开app就提醒升级
    public static final String SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME = "SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME";
    public static final String TEMP_PATH = "/tencent/vpn";
    public static final String VERSION_APP_INCOMING = "VERSION_APP_INCOMING"; // 服务器上的最新版本号
    public static final String ADS_ADVIEW_KEY = "SDK201613190108211j6pfsw630dnsyz";
    public static final String ADS_ADVIEW_KEY_ACTIVITY = "SDK20161823060827zy54tlysbkjtt4h";
    public static final String adsKeySet[] = new String[]{ADS_ADVIEW_KEY};
    public static final String adsKeySetBanner[] = new String[]{ADS_ADVIEW_KEY, ADS_ADVIEW_KEY_ACTIVITY};
    public static final String LANG_ZH = "zh";
    public static final String LANG_US = "en";
    public static VpnType mVpnType = VpnType.IKEV2_CERT;
    public static int connTimeOut = 20;
    public static String NET_ERROR = "network error";
    public static boolean USE_BYOD = false;
    public static VpnType vpnType = VpnType.IKEV2_EAP;
    public static String FIRST_FB_CLICK = "FIRST_FB_CLICK";
    public static int ADS_SHOW_SCORE = 10;
    public static int ADS_SHOW_CLICK = 20;

    public static String getRECOMMEND_URL(int start) {
        return String.format(API_RECOMMEND_URL, start);
    }

}
