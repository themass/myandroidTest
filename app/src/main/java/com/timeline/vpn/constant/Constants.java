package com.timeline.vpn.constant;


import com.timeline.vpn.service.utils.VpnType;

/**
 * Created by gqli on 2015/9/1.
 */
public class Constants {
    public static final String FIRSTRUN = "isFirstRun";
    public static final String LOCSDK = "VFNwiTplUvpm7iLaV6FBLNOkxFYrEbp1";
    public static final int SELECT_TRUSTED_CERTIFICATE = 0;
    public static final String DEFAULT_REFERER = "http://timeline.vpn.com/";
    public static final String BASE_HOST = "http://10.33.70.182:8080/vpn/api";//TODO 测试地址
    //    public static final String BASE_HOST = "http://192.168.1.8:8080/vpn/api";//TODO 测试地址
    public static final String SERVERLIST_URL = BASE_HOST + "/host/server/list.json";
    public static final String ADSSTRATEGY_URL = BASE_HOST + "/data/ads/strategy.json";
    public static final String LOCATION_URL = BASE_HOST + "/host/server/location.json";
    public static final String LOGIN_URL = BASE_HOST + "/user/login.json";
    public static final String REG_URL = BASE_HOST + "/user/reg.json";
    public static final String VERSION_URL = BASE_HOST + "/data/version.json";
    public static final String URL = "url";
    public static final int STARTUP_SHOW_TIME_1500 = 1500; //启动页广告时长
    public static final int STARTUP_SHOW_TIME_3500 = 3500; //启动页广告时长
    public static final int STARTUP_SHOW_TIME_8000 = 8000; //启动页广告时长
    public static final String LAUNCH_BAIDU = "baidu";
    public static final String LAUNCH_QQ = "qq";
    public static final String LAUNCH_YOUMI = "youmi";
    public static final String LAUNCH_DUOM = "duom";
    public static final int ADS_NO_MSG = 0;
    public static final int ADS_CLICK_MSG = 1;
    public static final int ADS_DISMISS_MSG = 2;
    public static final int ADS_PRESENT_MSG = 3;
    public static final int ADS_READY_MSG = 4;
    public static final int ADS_LOADINGPAGE_OPEN_MSG = 6;
    public static final int ADS_LOADINGPAGE_CLOSE_MSG = 6;
    /**
     * qq ads
     **/
    public static final String QQ_APPID = "1104745779";
    public static final String QQ_BANNERPOSID = "9079537218417626401";
    public static final String QQ_APPWALLPOSID = "9007479624379698465";
    public static final String QQ_INTERTERISTALPOSID = "8575134060152130849";
    public static final String QQ_SPLASHPOSID = "8863364436303842593";
    public static final String QQ_GRIDAPPWALLPOSID = "9007479624379698465";
    public static final String QQ_NATIVEPOSID = "5000709048439488";
    /**
     * youmi ads
     **/
    public static final String YOUMI_APPID = "85aa56a59eac8b3d";
    public static final String YOUMI_APPSECRET = "a14006f66f58d5d7";
    /**
     * baidu ads
     **/
    public static final String BAIDU_APPID = "e866cfb0";
    public static final String BAIDU_INTERTERISTALPOSID = "2403633";
    public static final String BAIDU_SPLASHPOSID = "2058622";
    public static final String BAIDU_BANNERID = "2015351";
    public static final String BAIDU_LISTVIEWID = "2058628";
    /**
     * duomeng ads
     **/
    public static final String DM_PUBLISHER_ID = "56OJyM1ouMGoaSnvCK";
    public static final String DM_INLINEPPID = "16TLwebvAchksY6iO_8oSb-i";
    public static final String DM_INTERSTITIALPPID = "16TLwebvAchksY6iOa7F4DXs";
    public static final String DM_SPLASHPPID = "16TLwebvAchksY6iOGe3xcik";
    public static final int UM_INTERVAL = 40;
    public static final int NO_VALUE_FLAG = -999;//无
    public static final int SUNNY = 0;//晴
    public static final int CLOUDY = 1;//多云
    public static final int OVERCAST = 2;//阴
    public static final int FOGGY = 3;//雾
    public static final int SEVERE_STORM = 4;//飓风
    public static final int HEAVY_STORM = 5;//大暴风雨
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
    public static final int STORM = 6;//暴风雨
    public static final int THUNDERSHOWER = 7;//雷阵雨
    public static final int SHOWER = 8;//阵雨
    public static final int HEAVY_RAIN = 9;//大雨
    public static final int MODERATE_RAIN = 10;//中雨
    public static final int LIGHT_RAIN = 11;//小雨
    public static final int SLEET = 12;//雨夹雪
    public static final int SNOWSTORM = 13;//暴雪
    public static final int SNOW_SHOWER = 14;//阵雪
    public static final int HEAVY_SNOW = 15;//大雪
    public static final int MODERATE_SNOW = 16;//中雪
    public static final int LIGHT_SNOW = 17;//小雪
    public static final int STRONGSANDSTORM = 18;//强沙尘暴
    public static final int SANDSTORM = 19;//沙尘暴
    public static final int SAND = 20;//沙尘
    public static final int BLOWING_SAND = 21;//风沙
    public static final int ICE_RAIN = 22;//冻雨
    public static final int DUST = 23;//尘土
    public static final int HAZE = 24;//霾
    public static final String CITY_KEY = "city";
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
    public static final String DEVID = "DEVID";
    public static final String HTTP_TOKEN_KEY = "timeline-vpn-token";
    public static final String SEX_M = "M";
    public static final String SEX_F = "F";
    public static final int HTTP_SUCCESS = 0;
    // 有新版本时，是否打开app就提醒升级
    public static final String SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME = "SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME";
    public static final String TEMP_PATH = "/tencent/vpn";
    public static final String VERSION_APP_INCOMING = "VERSION_APP_INCOMING"; // 服务器上的最新版本号
    public static final String ADS_ADVIEW_KEY = "SDK20161016100633tmbx713x53gxqj4";
    public static final String adsKeySet[] = new String[]{ADS_ADVIEW_KEY};
    private static final String RECOMMEND_URL = BASE_HOST + "/data/recommend.json?start=%s&limit=30";
    public static VpnType mVpnType = VpnType.IKEV2_CERT;
    public static int connTimeOut = 20;
    public static String NET_ERROR = "network error";
    public static boolean USE_BYOD = false;
    public static VpnType vpnType = VpnType.IKEV2_EAP;

    public static String getRECOMMEND_URL(int start) {
        return String.format(RECOMMEND_URL, start);
    }


}
