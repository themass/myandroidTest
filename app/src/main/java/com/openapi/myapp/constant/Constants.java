package com.openapi.myapp.constant;



import com.openapi.yewu.ads.adview.AdviewConstant;

import org.strongswan.android.logic.VpnType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by dengt on 2015/9/1.
 */
public class Constants {

    public static final String USER_AGENT = "User-Agent";
    public static final String USER_AGENT_DEF = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36";
    public static final String REFERER = "referer";
    public static final String DEFAULT_REFERER = "http://www.ok123find.top/";
    public static final String URL = "url";
    public static final String DEFULT_LOCATION_NAME="Random";
    public static final int STARTUP_SHOW_TIME_6000 = 6000; //启动页广告时长
    public static final int STARTUP_SHOW_TIME_7000 = 7000; //启动页广告时长
    public static final int BANNER_ADS_GONE_LONG = 7000; //广告显示时长
    public static final int BANNER_ADS_GONE_LONG_LONG = 240000; //广告显示时长
    public static final int BANNER_ADS_GONE_SHORT = 5000; //启动页广告时长
    public static final int VIP_SHIMMER_DURATION = 2300;
    public static final int RECOMMAND_SHIMMER_DURATION = 1500;
    public static final int VPN_CHECK_TIME = 6000; //启动页广告时长
    public static int PROBABILITY =4;
    public static final int ADS_JISHI = -1;
    public static final String HTTP_URL = "http";
    public static final String HTTPS_URL = "https";
    public static final String BROWSER_URL = "browser";
    public static final String URL_TMP = "://";
    //location
    public static final String LOCATION_CHOOSE = "LOCATION_CHOOSE1";
    public static final String LOCATION_FLAG = "LOCATION_FLAG";
    public static final String LOCATION_FLAG_COUNT = "LOCATION_FLAG_COUNT";
    public static final List<Integer> BANNER_ADS_POS = Arrays.asList(1,4,9,12);

    public static final String sortType = "type";
    public static final String sortCountry = "ename";
    public static final String sortASC = "asc";

    //user
    public static final String LOGIN_USER = "LOGIN_USER";
    public static final String LOGIN_USER_LAST = "LOGIN_USER_LAST";
    public static final String SOUND_SWITCH = "SOUND_SWITCH";
    public static final String NOTIFY_SWITCH = "NOTIFY_SWITCH";
    public static final String LOGIN_USER_PW_LAST = "LOGIN_USER_PW_LAST";
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
    public static final int HTTP_LOGIN = 2;
    public static final String USERIP = "USERIP";
    public static final String CLICK_KEY="CLICK_KEY";
    public static final String TRAF_KEY="TRAF_KEY";

    public static final String DEFAULT_FEEDBACK_APPKEY = "23575056";
    public static final String SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME = "SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME";
    public static final String TEMP_PATH = "/AFreedom/free";
    public static final String VERSION_APP_INCOMING = "VERSION_APP_INCOMING";

    public static final String API_SERVERLIST_URL = "/host/server/list.json?location=%s";
//    public static final String API_FAB_ADSCLICK_URL = "/user/ads/score.json?score=%s";
    public static final String API_FAB_ADSCLICK2_URL = "/user/ads/check.json";
    public static final String API_LOCATION_URL = "/host/server/location/cache.json?type=%s";
    public static final String API_LOCATION_VIP_URL = "/host/server/location/vip/cache.json";
    public static final String API_LOGIN_URL = "/user/login.json";
    public static final String API_REG_URL = "/user/reg.json";
    public static final String API_USER_INFO_URL = "/user/info.json";

    public static final String API_IWANNA_URL = "/data/feed/wanna.json?start=%s&limit=30";
    public static final String API_IWANNA_LIKE_URL = "/data/feed/wanna/%s.json";

    public static final String API_FEEDBACK_URL = "/data/feed/score.json?start=%s&limit=40";
    public static final String API_FEEDBACK_LIKE_URL = "/data/feed/score/%s.json";

    public static final String API_VERSION_URL = "/data/version.json";
    public static final String API_DOMAIN_URL = "/data/domain.json";
    public static final String API_RECOMMEND_URL = "/data/recommend.json?start=%s&limit=20";
    public static final String API_RECOMMEND_CUSTOME_URL = "/data/recommend/custome.json?start=%s&limit=100";

    public static final String API_LOGOUT_URL = "/user/logout.json";
    public static final String API_LOG_URL = "/monitor/bug.json";
    public static final String API_CONNLOG_URL = "/monitor/connlog.json";
    public static final String API_ADD_CUSTOME = "/user/custome/add.json";
    public static final String API_DEL_CUSTOME = "/user/custome/del.json";
    public static final String API_EMU = "/monitor/emulator.json";
    public static final String API_DONATION_URL = "/data/donation.json";


    public static final VpnType mVpnType = VpnType.IKEV2_CERT;
    public static final int connTimeOut = 20;
    public static final VpnType vpnType = VpnType.IKEV2_EAP;
    public static final String NET_ERROR = "network error";
    public static final int ADS_SHOW_SCORE = 10;
    public static final int ADS_SHOW_CLICK = 30;
    public static final int ADS_REWARD_SHOW_CLICK = 100;

    public static final String D_URL = "D_URL";
    public static final String ADS_SHOW_CONFIG = "ADS_SHOW_CONFIG";
    public static final String ADS_POP_SHOW_CONFIG = "ADS_POP_SHOW_CONFIG";
    public static final String LOG_UPLOAD_CONFIG = "LOG_UPLOAD_CONFIG";
    public static final String NEED_DNSPOD_CONFIG = "NEED_DNSPOD_CONFIG";
    public static final String NEED_NATIVE_ADS_CONFIG = "NEED_NATIVE_ADS_CONFIG";
    public static final String IS_CHAIN = "IS_CHAIN";
    public static final String CHECK_CHAIN = "CHECK_CHAIN";
    public static final String USER_IP = "USER_IP";
    public static final String CONFIG_PARAM = "CONFIG_PARAM";
    public static final String TITLE = "TITLE";
    public static final String ADSSHOW = "ADSSHOW";
    public static final String FILE_UPLOAD = "fileList";
    public static final int maxRate = 20;
    public static final String IMG_ITEMS = "IMG_ITEMS";
    public static final String SCORE_TMP = "SCORE_TMP";
    public static final String SCORE_CLICK = "SCORE_CLICK";
    public static final String SCORE_CLICK_CLICK = "SCORE_CLICK_CLICK";
    public static final long SCORE_CLICK_INTERVAL = 10;
    public static final String VPN_STATUS = "VPN_STATUS";
    public static final int MAX_RETRY_COUNT = 4;
    public static final String ADMIN = "themass";
    public static final String CUSTOME_SORT = "CUSTOME_SORT";
    public static final String LOCATION_ICON_ALL ="timeline://img/flag_all.png";
    public static String BASE_IP = "api.ok123find.top";
    public static String BASE_IP_BAK = "api.faceopen.top";
    public static String ABOUT_FIRST = "ABOUT_FbbIRST";
    public static String ABOUT = "http://file.ok123find.top/about.html";

    public static String IP_ADDRESS = "http://www.geoplugin.net/json.gp?ip=";
    public static String USER_STATUS = "USER_STATUS";
    //        public static String BASE_IP = "192.168.1.12:8080";
//    public static String BASE_IP = "10.33.65.180:8080";
    public static final String downloadUrl="https://play.google.com/store/apps/details?id=com.openapi.ks.free1";
    public static int NULL_VIEW = -1;
    public static List<String> colorBg = Arrays.asList("#552d5d82", "#55135689", "#552292e9", "#5583878b", "#7f8d8f45", "#ffc49924", "#ff83713f", "#ff569b2b", "#ff882b9b");

    public static String getUrl(String uri) {
        return "http://" + BASE_IP + "/vvv/api" + uri;
    }
    public static String getUrlHost(String uri) {
        return "http://" + BASE_IP_BAK + "/vvv/api" + uri;
    }
    public static String getUrlWithParam(String url, Object... param) {
        return String.format(getUrl(url), param);
    }
    public static class UserLevel {
        public static final int LEVEL_FREE = 0;
        public static final int LEVEL_VIP = 1;
        public static final int LEVEL_VIP2 = 2;
        public static final int LEVEL_VIP3 = 3;
        public static final int LEVEL_VIP4 = 4;
    }

    public static class OpenUrlPath {
        public static final int local = 0;
        public static final int browser = 1;
    }

    public static class ShowType {
        public static final int Normal = 0;
        public static final int Blur = 1;
        public static final int Text = 2;
        public static final int Text_Below = 3;
    }
    public static final String APPID = "1108006908";
    public static final String NativeExpressPosID = "6010441996206888";
    public static final String NativeExpressPosID_2 = "1090544966701912";
    public static final int AD_COUNT = 8;    // 加载广告的条数，取值范围为[1, 10]
    public static int FIRST_AD_POSITION = 1; // 第一条广告的位置
    public static int ITEMS_PER_AD_SIX = 6;     // 每间隔10个条目插入一条广告
    public static int ITEMS_PER_AD_THREE = 3;     // 每间隔10个条目插入一条广告
    public static int ITEMS_PER_AD_BANNER = 1;     // 每间隔10个条目插入一条广告
    public static final String OpenExpressPosID = "9060242730064757";
    public static final String InterExpressPosID = "7040843730666709";
    public static final String InterExpressPosID_1 = "9060947720962850";
    public static final String InterExpressPosID_2 = "9000343720565852";
    public static final String InterExpressPosID_3 = "1010745790065883";


    public static final String ADMOB_REWARD_ID_TEST="ca-app-pub-3940256099942544/5224354917";
    public static final String ADMOB_REWARD_ID="ca-app-pub-6599725010915516/3828026144";

    public static final String ADMOB_BANNER_ID="ca-app-pub-6599725010915516/9607553162";
    public static final String ADMOB_BANNER_ID_TEST="ca-app-pub-3940256099942544/6300978111";
    public static final String ADMOB_BANNER_ID2="ca-app-pub-6599725010915516/3259822574";

    public static final String ADMOB_INTER_ID="ca-app-pub-6599725010915516/3216323064";
    public static final String ADMOB_INTER_ID_TEST="ca-app-pub-3940256099942544/1033173712";

    public static final String ADMOB_SPLASH_ID="ca-app-pub-6599725010915516/2591019995";
    public static final String ADMOB_SPLASH_ID_TEST="ca-app-pub-3940256099942544/3419835294";



    public static final String ADMOB_REWARD_UNIT_ID="ca-app-pub-7849865307083433/1108754074";
    public static final String ADMOB_REWARD_UNIT_ID2="ca-app-pub-7849865307083433/1842540805";
    public static final String ADMOB_REWARD_UNIT_ID3="ca-app-pub-7849865307083433/5020078921";
    public static final List<String> ADMOB_REWARD_UNIT_IDS=Arrays.asList(ADMOB_REWARD_UNIT_ID,ADMOB_REWARD_UNIT_ID,ADMOB_REWARD_UNIT_ID);



    public static final String Mob_APPID_TEST="144002";
    public static final String Mob_APPKEY_TEST="7c22942b749fe6a6e361b675e96b3ee9";
    public static final String Mob_SPLASH_UNIT_TEST="328916";
    public static final String Mob_SPLASH_UNIT_PLACE_TEST="1542060";
    public static final String Mob_INTER_UNIT_TEST="290653";
    public static final String Mob_INTER_UNIT_PLACE_TEST="462374";
    public static final String Mob_REWARD_UNIT_TEST="290651";
    public static final String Mob_REWARD_UNIT_PLACE_TEST="462372";

    public static final String Mob_UNIT_BANNER_PLACE_TEST="290655";
    public static final String Mob_UNIT_BANNER1_TEST="462376";
    public static final String Mob_UNIT_BANNER2_TEST="462376";
    public static final String Mob_UNIT_BANNER3_TEST="462376";
    public static final String Mob_UNIT_BANNER_TEST="462376";

    public static final String Mob_APPID="209825";
    public static final String Mob_APPKEY="f1f0a8637166a0459402b9de3c88a93f";
    public static final String Mob_SPLASH_UNIT="809686";
    public static final String Mob_SPLASH_UNIT_PLACE="2392263";
    public static final String Mob_INTER_UNIT="809698";
    public static final String Mob_INTER_UNIT_PLACE="2392276";
    public static final String Mob_REWARD_UNIT="809687";
    public static final String Mob_REWARD_UNIT_PLACE="2392264";

    public static final String Mob_UNIT_BANNER_PLACE="811116";
    public static final String Mob_UNIT_BANNER1="2394073";
    public static final String Mob_UNIT_BANNER2="2394071";
    public static final String Mob_UNIT_BANNER3="2394069";
    public static final String Mob_UNIT_BANNER="2394067";

    public static final String Mob_UNIT_WALL="88840";

    public static Map<String ,String> adviewToMobvBanner = new HashMap<>();
    static {
        adviewToMobvBanner.put(AdviewConstant.ADS_ADVIEW_KEY1,Mob_UNIT_BANNER1);
        adviewToMobvBanner.put(AdviewConstant.ADS_ADVIEW_KEY2,Mob_UNIT_BANNER2);
        adviewToMobvBanner.put(AdviewConstant.ADS_ADVIEW_KEY,Mob_UNIT_BANNER);
    }
    public static Map<String ,String> adviewToAdmobBanner = new HashMap<>();
    static {
        adviewToAdmobBanner.put(AdviewConstant.ADS_ADVIEW_KEY1,ADMOB_BANNER_ID2);
        adviewToAdmobBanner.put(AdviewConstant.ADS_ADVIEW_KEY2,ADMOB_BANNER_ID);
        adviewToAdmobBanner.put(AdviewConstant.ADS_ADVIEW_KEY,ADMOB_BANNER_ID);
    }

}
