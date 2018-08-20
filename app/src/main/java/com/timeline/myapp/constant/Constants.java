package com.timeline.myapp.constant;



import com.timeline.vpn.R;

import org.strongswan.android.logic.VpnType;

import java.util.Arrays;
import java.util.List;
/**
 * Created by themass on 2015/9/1.
 */
public class Constants {

    public static final String USER_AGENT = "User-Agent";
    public static final String USER_AGENT_DEF = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36";
    public static final String REFERER = "referer";
    public static final String DEFAULT_REFERER = "http://www.sspacee.com/";
    public static final String IMAGE_RES_PRE = "timeline://img";
    public static final String APP_MYPOOL = "MYPOOL";
    public static final String APP_GOOGLE = "GOOGLEMARKET";
    public static final String URL = "url";
    public static final String DEFULT_LOCATION_NAME="随机";
    public static final int STARTUP_SHOW_TIME_6000 = 4000; //启动页广告时长
    public static final int STARTUP_SHOW_TIME_7000 = 4000; //启动页广告时长
    public static final int BANNER_ADS_GONE_LONG = 13000; //广告显示时长
    public static final int BANNER_ADS_GONE_LONG_LONG = 240000; //广告显示时长
    public static final int BANNER_ADS_GONE_SHORT = 5000; //启动页广告时长
    public static final int VIP_SHIMMER_DURATION = 2300;
    public static final int RECOMMAND_SHIMMER_DURATION = 1500;
    public static final int ADS_JISHI = -1;
    public static final String QQ = "QQ";
    public static final String HTTP_URL = "http";
    public static final String HTTPS_URL = "https";
    public static final String BROWSER_URL = "browser";
    public static final String SOUND_URL = "sound";
    public static final String TEXT_URL = "text";
    public static final String IMG_URL = "img";
    public static final String VIDEO_URL = "video";
    public static final String VIDEOLIST_URL = "videoList";
    public static final String URL_TMP = "://";
    //location
    public static final String LOCATION_CHOOSE = "LOCATION_CHOOSE1";
    public static final String LOCATION_FLAG = "LOCATION_FLAG";
    public static final List<Integer> BANNER_ADS_POS = Arrays.asList(2,9,16,23);
    public static final String sortType = "type";
    public static final String sortCountry = "ename";
    public static final String sortFea = "level";
    public static final String sortASC = "asc";
    public static final String sortDESC = "desc";
    //user
    public static final String LOGIN_USER = "LOGIN_USER";
    public static final String LOGIN_USER_LAST = "LOGIN_USER_LAST";
    public static final String SOUND_SWITCH = "SOUND_SWITCH";
    public static final String TEST_SWITCH = "TEST_SWITCH";
    public static final String NOTIFY_SWITCH = "NOTIFY_SWITCH";
    public static final String LOGIN_USER_PW_LAST = "LOGIN_USER_PW_LAST";
    public static final String DEVID = "Devid";
    public static final String HTTP_TOKEN_KEY = "Vpn-Token";
    public static final String ALERT = "ALERT";
    public static final String HTTP_LANG = "lang";
    public static final String LON = "lon";
    public static final String LAT = "lat";
    public static final String PORT = "port";
    public static final String SEX_M = "M";
    public static final String SEX_F = "F";
    public static final String VPNB = "VPNB";
    public static final int HTTP_SUCCESS = 0;
    public static final int HTTP_SUCCESS_CLEAR = 1;
    public static final String USERIP = "USERIP";
    // 有新版本时，是否打开app就提醒升级
    //用户反馈
    public static final String SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME = "SETTING_PREF_NEED_CHECK_UPDATE_NEXT_TIME";
    public static final String TEMP_PATH = "/tencent/vpn";
    public static final String VERSION_APP_INCOMING = "VERSION_APP_INCOMING"; // 服务器上的最新版本号

    public static final String API_FINDPASS_URL = "/user/findpass.json";
    public static final String API_SETEMAIL_URL = "/user/setemail.json";
    public static final String API_SERVERLIST_URL = "/host/server/list.json?location=%s";
    public static final String API_FAB_ADSCLICK_URL = "/user/ads/score.json?score=%s";
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
    public static final String API_APP_URL = "/data/appinfo.json";
    public static final String API_DONATION_URL = "/data/donation.json";

    public static final String API_RECOMMEND_URL = "/data/recommend.json?start=%s&limit=20";
    public static final String API_RECOMMEND_VIP_URL = "/data/recommend/vip.json?start=%s&limit=20";
    public static final String API_RECOMMEND_CUSTOME_URL = "/data/recommend/custome.json?start=%s&limit=100";

    public static final String API_SOUND_CHANNLE_URL = "/sound/channel.json?start=%s&limit=20&channel=%s";
    public static final String API_SOUND_ITEMS_URL = "/sound/items.json?start=%s&limit=30&channel=%s&keyword=%s";

    public static final String API_TEXT_CHANNLE_URL = "/text/channel.json?start=%s&limit=50&channel=%s";
    public static final String API_TEXT_ITEMS_URL = "/text/items.json?start=%s&limit=60&channel=%s&keyword=%s";
    public static final String API_TEXT_ITEM_URL = "/text/item.json?id=%s";

    public static final String API_IMG_CHANNLE_URL = "/img/channel.json?start=%s&limit=50&channel=%s";
    public static final String API_IMG_ITEMS_IMG_URL = "/img/items/img.json?start=%s&limit=30&channel=%s&keyword=%s";
    public static final String API_IMG_ITEM_PAGE_URL = "/img/item/page.json?start=%s&limit=30&itemUrl=%s";

    public static final String API_VIDEO_CHANNLE_URL = "/video/channel.json?start=%s&limit=50&channel=%s";
    public static final String API_VIDEO_CHANNEL_LIST_URL = "/video/channel/items.json?start=%s&limit=30&channel=%s&keyword=%s";
    public static final String API_VIDEO_USER_URL = "/video/user.json?channel=%s&start=%s&limit=30";
    public static final String API_VIDEO_USER_ITEM_URL = "/video/user/items.json?start=%s&limit=30&userId=%s&keyword=%s";
    public static final String API_RECOMMEND_MOVIE_URL = "/data/recommend/movie.json?start=%s&limit=20";

    public static final String API_LOGOUT_URL = "/user/logout.json";
    public static final String API_LOG_URL = "/monitor/bug.json";
    public static final String API_CONNLOG_URL = "/monitor/connlog.json";
    public static final String API_ADD_CUSTOME = "/user/custome/add.json";
    public static final String API_DEL_CUSTOME = "/user/custome/del.json";

    public static final String VIDEO_USER_CHANNEL = "qvod_user";
    public static final String CHANNEL = "channel";

    public static final int connTimeOut = 20;
    public static final VpnType vpnType = VpnType.IKEV2_EAP;
    public static final String NET_ERROR = "network error";
    public static final int ADS_SHOW_SCORE = 10;
    public static final int ADS_SHOW_CLICK = 30;
    public static final String D_URL = "D_URL";
    public static final String ADS_SHOW_CONFIG = "ADS_SHOW_CONFIG";
    public static final String ADS_POP_SHOW_CONFIG = "ADS_POP_SHOW_CONFIG";
    public static final String LOG_UPLOAD_CONFIG = "LOG_UPLOAD_CONFIG";
    public static final String NEED_DNSPOD_CONFIG = "NEED_DNSPOD_CONFIG";
    public static final String NEED_NATIVE_ADS_CONFIG = "NEED_NATIVE_ADS_CONFIG";
    public static final String CONFIG_PARAM = "CONFIG_PARAM";
    public static final String TITLE = "TITLE";
    public static final String FILE_UPLOAD = "fileList";
    public static final String SOUND_CHANNEL = "SOUND_CHANNEL";
    public static final String TEXT_FILE = "TEXT_FILE";
    public static final String TEXT_CHANNEL = "TEXT_CHANNEL";
    public static final String IMG_CHANNEL = "IMG_CHANNEL";
    public static final String VIDEO_CHANNEL = "IMG_CHANNEL";
    public static final String VIDEO_TYPE_NORMAL= "normal";
    public static final int maxRate = 10;
    public static final String IMG_ITEMS = "IMG_ITEMS";
    public static final String SCORE_TMP = "SCORE_TMP";
    public static final String SCORE_CLICK = "SCORE_CLICK";
    public static final long SCORE_CLICK_INTERVAL = 2;
    public static final String VPN_STATUS = "VPN_STATUS";
    public static final String ADMIN = "themass";
    public static final String LOCATION_ICON_ALL ="timeline://img/flag_all.png";
    public static final int[] img = new int[]{R.drawable.bg_api};
    public static String BASE_IP = "api.secondary.space";
    public static String BASE_IP_BAK = "api.sspacee.com";
    public static String ABOUT_FIRST = "ABOUT_FbbIRST";
    public static String ABOUT_ZH = "http://file.sspacee.com/file/html/about_wanji_zh.html";
    public static String USER_STATUS = "USER_STATUS";
    public static List<String> colorBg = Arrays.asList("#552d5d82", "#55135689", "#552292e9", "#5583878b", "#7f8d8f45", "#ffc49924", "#ff83713f", "#ff569b2b", "#ff882b9b");

    public static String getUrl(String uri) {
        return "http://" + BASE_IP + "/vpn/api" + uri;
    }

    public static String getUrlWithParam(String url, Object... param) {
        return String.format(getUrl(url), param);
    }
    public static class FavoriteType {
        public static final int TEXT = 0;
        public static final int SOUND = 1;
        public static final int IMG = 2;
        public static final int VIDEO = 3;
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
}