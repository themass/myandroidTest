package com.qq.myapp.constant;



import com.qq.ks.free1.R;
import com.qq.yewu.ads.adview.AdviewConstant;

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
    public static final String DEFAULT_REFERER = "http://www.sspacee.com/";
    public static final String DEFAULT_API = "api.sspacee.com";
    public static final String IMAGE_RES_PRE = "timeline://img";
    public static final String URL = "url";
    public static final String DEFULT_LOCATION_NAME="Random";
    public static final int STARTUP_SHOW_TIME_6000 = 3000; //启动页广告时长
    public static final int STARTUP_SHOW_TIME_7000 = 6000; //启动页广告时长
    public static final int BANNER_ADS_GONE_LONG = 7000; //广告显示时长
    public static final int BANNER_ADS_GONE_LONG_LONG = 240000; //广告显示时长
    public static final int BANNER_ADS_GONE_SHORT = 5000; //启动页广告时长
    public static final int VIP_SHIMMER_DURATION = 2300;
    public static final int RECOMMAND_SHIMMER_DURATION = 1500;
    public static final int VPN_CHECK_TIME = 6000; //启动页广告时长
    public static int PROBABILITY =7;
    public static final int ADS_JISHI = -1;
    public static final String HTTP_URL = "http";
    public static final String HTTPS_URL = "https";
    public static final String BROWSER_URL = "browser";
    public static final String URL_TMP = "://";
    //location
    public static final String LOCATION_CHOOSE = "LOCATION_CHOOSE1";
    public static final String LOCATION_FLAG = "LOCATION_FLAG";
    public static final String LOCATION_FLAG_COUNT = "LOCATION_FLAG_COUNT";
    public static final List<Integer> BANNER_ADS_POS = Arrays.asList(1,6,17,26);
    public static final List<Integer> BANNER_ADS_POS1 = Arrays.asList(3,10);

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

    // 有新版本时，是否打开app就提醒升级
    //用户反馈
    public static final String DEFAULT_FEEDBACK_APPKEY = "23575056";
    public static final String SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME = "SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME";
    public static final String TEMP_PATH = "/freevpn/free";
    public static final String VERSION_APP_INCOMING = "VERSION_APP_INCOMING"; // 服务器上的最新版本号

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
    public static final String CONFIG_PARAM = "CONFIG_PARAM";
    public static final String TITLE = "TITLE";
    public static final String ADSSHOW = "ADSSHOW";
    public static final String FILE_UPLOAD = "fileList";
    public static final int maxRate = 10;
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
    public static String BASE_IP = "api.sspacee.com";
    public static final String HOSTGGG = "api.hostggg.com";
    public static String ABOUT_FIRST = "ABOUT_FbbIRST";
    public static String ABOUT_ZH = "http://file.sspacee.com/file/html/about.html";
    public static String ABOUT = "http://file.sspacee.com/file/html/about.html";
    public static String USER_STATUS = "USER_STATUS";
    //        public static String BASE_IP = "192.168.1.12:8080";
//    public static String BASE_IP = "10.33.65.180:8080";
    public static String BASE_HOST = "http://" + BASE_IP + "/vvv/api";
    public static final String downloadUrl="https://play.google.com/store/apps/details?id=com.qq.fq2";
    public static int NULL_VIEW = -1;
    public static List<String> colorBg = Arrays.asList("#552d5d82", "#55135689", "#552292e9", "#5583878b", "#7f8d8f45", "#ffc49924", "#ff83713f", "#ff569b2b", "#ff882b9b");

    public static String getUrl(String uri) {
        return "http://" + BASE_IP + "/vvv/api" + uri;
    }
    public static String getUrlHost(String uri) {
        return "http://" + HOSTGGG + "/vvv/api" + uri;
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


    public static final String ADMOB_REWARD_ID="ca-app-pub-7849865307083433~9233060875";
    public static final String ADMOB_REWARD_UNIT_ID="ca-app-pub-7849865307083433/1108754074";
    public static final String ADMOB_REWARD_UNIT_ID2="ca-app-pub-7849865307083433/1842540805";
    public static final String ADMOB_REWARD_UNIT_ID3="ca-app-pub-7849865307083433/5020078921";
    public static final List<String> ADMOB_REWARD_UNIT_IDS=Arrays.asList(ADMOB_REWARD_UNIT_ID,ADMOB_REWARD_UNIT_ID,ADMOB_REWARD_UNIT_ID);

    public static final String INMOBI_APPID="6cc6e6f318a04869a33c79f4b9d71e27";
    public static final String INMOBI_ACCOUNTID="e5b7a52a95cd4802b49b279caa0e2c0d";
    public static final Long INMOBI_APPKEY=1555588600038l;


    public static final String Mob_APPID="114279";
    public static final String Mob_APPKEY="f1f0a8637166a0459402b9de3c88a93f";
    public static final String Mob_UNIT_REWARD="88846";
    public static final String Mob_UNIT_INTV="88839";

    public static final String Mob_UNIT_BANNER1="88845";
    public static final String Mob_UNIT_BANNER2="88844";
    public static final String Mob_UNIT_BANNER3="88843";
    public static final String Mob_UNIT_BANNER="88842";

    public static final String Mob_UNIT_WALL="88840";

    public static final String Mob_UNIT_SPLASH="88838";
    public static final String Mob_UNIT_SPLASH_KAY="Mob_UNIT_SPLASH_KAY";


    public static Map<String ,String> adviewToMobvBanner = new HashMap<>();
    static {
        adviewToMobvBanner.put(AdviewConstant.ADS_ADVIEW_KEY1,Mob_UNIT_BANNER1);
        adviewToMobvBanner.put(AdviewConstant.ADS_ADVIEW_KEY2,Mob_UNIT_BANNER2);
        adviewToMobvBanner.put(AdviewConstant.ADS_ADVIEW_KEY4,Mob_UNIT_BANNER3);
        adviewToMobvBanner.put(AdviewConstant.ADS_ADVIEW_KEY,Mob_UNIT_BANNER);
    }
}
