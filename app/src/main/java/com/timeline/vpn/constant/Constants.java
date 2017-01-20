package com.timeline.vpn.constant;


import org.strongswan.android.logic.VpnType;

/**
 * Created by themass on 2015/9/1.
 */
public class Constants {
    public static final String REFERER = "referer";
    public static final String DEFAULT_REFERER = "http://www.sspacee.com/";
    public static final String URL = "url";
    public static final int STARTUP_SHOW_TIME_5000 = 5000; //启动页广告时长
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
    public static final int ADS_TYPE_ERROR = -1;
    public static final int ADS_TYPE_BANNER = 0;
    public static final int ADS_TYPE_SPREAD = 1;
    public static final int ADS_TYPE_INTERSTITIAL = 2;
    public static final int ADS_TYPE_NATIVE = 3;
    public static final int UM_INTERVAL = 40;
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
    public static final String MY_PUSH_TYPE = "mypushtype";
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
    public static final String LON = "lon";
    public static final String LAT = "lat";
    public static final String PORT = "port";
    public static final String SEX_M = "M";
    public static final String SEX_F = "F";
    public static final int HTTP_SUCCESS = 0;
    public static final int HTTP_SUCCESS_CLEAR = 1;
    // 有新版本时，是否打开app就提醒升级
    //用户反馈
    public static final String DEFAULT_FEEDBACK_APPKEY = "23575056";
    public static final String SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME = "SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME";
    public static final String TEMP_PATH = "/tencent/vpn";
    public static final String VERSION_APP_INCOMING = "VERSION_APP_INCOMING"; // 服务器上的最新版本号
    public static final String ADS_ADVIEW_KEY = "SDK201614200212284mmevtthxxbrcsb";
    public static final String ADS_ADVIEW_KEY_ACTIVITY = "SDK201614200212284mmevtthxxbrcsb";
    public static final String adsKeySet[] = new String[]{ADS_ADVIEW_KEY};
    public static final String adsKeySetBanner[] = new String[]{ADS_ADVIEW_KEY, ADS_ADVIEW_KEY_ACTIVITY};
    public static final String LANG_ZH = "zh";
    public static final String LANG_US = "en";
    public static final String API_SERVERLIST_URL = "/host/server/list.json?location=%s";
    public static final String API_FAB_ADSCLICK_URL = "/user/ads/score.json?score=%s";
    public static final String API_LOCATION_URL = "/host/server/location.json";
    public static final String API_LOGIN_URL = "/user/login.json";
    public static final String API_REG_URL = "/user/reg.json";
    public static final String API_REG_CODE_URL = "/user/code.json";
    public static final String API_USER_INFO_URL = "/user/info.json";
    public static final String API_IWANNA_URL = "/data/feed/wanna.json?start=%s&limit=20";
    public static final String API_IWANNA_LIKE_URL = "/data/feed/wanna/%s.json";
    public static final String API_VERSION_URL = "/data/version.json";
    public static final String API_RECOMMEND_URL = "/data/recommend.json?start=%s&limit=20";
    public static final String API_LOGOUT_URL = "/user/logout.json";
    public static final String API_LEAK_URL = "/monitor/leak.json";
    public static final String API_LOG_URL = "/monitor/bug.json";
    public static final VpnType mVpnType = VpnType.IKEV2_CERT;
    public static final int connTimeOut = 20;
    public static final String NET_ERROR = "network error";
    public static final VpnType vpnType = VpnType.IKEV2_EAP;
    public static final int ADS_SHOW_SCORE = 10;
    public static final int ADS_SHOW_CLICK = 20;
    public static final String D_URL = "D_URL";
    public static final String ADS_SHOW_CONFIG = "ADS_SHOW_CONFIG";
    public static final String LOG_UPLOAD_CONFIG = "LOG_UPLOAD_CONFIG";
    public static final String CONFIG_PARAM = "CONFIG_PARAM";
    public static final String TITLE = "TITLE";
    public static final String ADSSHOW = "ADSSHOW";
    public static final String FILE_UPLOAD = "fileList";
    public static final String FILE_TMP_PATH = "freeVPN";
    public static final String LOG_FILE = "charon.log";
    public static final String LOG_FILE_FOR_UPLOAD = "charon_upload.log";
    public static final String BUG_FILE = "log.log";
    public static final String BUG_FILE_FOR_UPLOAD = "log_upload.log";
    public static final String SCORE_TMP = "SCORE_TMP";
    public static final int MAX_RETRY_COUNT = 4;
    public static String BASE_IP = "api.sspacee.com";
    //    public static String BASE_IP = "192.168.1.7:8080";
//    public static String BASE_IP = "10.33.65.180:8080";
    public static String BASE_HOST = "http://" + BASE_IP + "/vpn/api";

    public static String getUrl(String uri) {
        return "http://" + BASE_IP + "/vpn/api" + uri;
    }

    public static String getRECOMMEND_URL(int start) {
        return String.format(getUrl(API_RECOMMEND_URL), start);
    }

}
