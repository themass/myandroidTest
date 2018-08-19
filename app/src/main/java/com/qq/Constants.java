package com.qq;



import android.content.Context;

import com.android.volley.Response;
import com.qq.network.R;

import org.strongswan.android.logic.VpnType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by dengt on 2015/9/1.
 */
public class Constants {

    public static final String USER_AGENT = "User-Agent";
    public static final String REFERER = "referer";
    public static final String DEFAULT_REFERER = "http://api.sspacee.com";
    public static final String IMAGE_CONF = "timeline://img";
    public static final String URL = "url";
    public static final String DEFULT_LOCATION_NAME="随机";
    public static final int STARTUP_SHOW_TIME_3000 = 2000; //启动页广告时长
    public static final int SHIMMER_DURATION = 1500;
    public static final String HTTP_URL = "http";
    public static final String HTTPS_URL = "https";
    public static final String LOCATION_FLAG = "LOCATION_FLAG";
    public static final String LOGIN_USER = "LOGIN_USER";
    public static final String LOGIN_USER_LAST = "LOGIN_USER_LAST";
    public static final String LOGIN_USER_PW_LAST = "LOGIN_USER_PW_LAST";
    public static final String HTTP_TOKEN_KEY = "Vpn-Token";

    public static final int SKIP_SLOW= -1;
    public static final Pattern namePattern = Pattern.compile("[a-zA-Z0-9]{3,20}");
    public static final Pattern passPattern = Pattern.compile("[0-9A-Za-z]{6,10}");
    public static final String[] sort_type = new String[]{"type_asc", "type_desc"};
    public static final String[] sort_country = new String[]{"ename_asc", "ename_desc"};
    public static final String DEVID = "Devid";
    public static final String LON = "lon";
    public static final String LAT = "lat";
    public static final int HTTP_SUCCESS = 0;
    public static final String API_SERVERLIST_URL = "/host/server/list.json?location=%s";
    public static final String API_LOCATION_URL = "/host/server/location/cache.json?type=%s";
    public static final String API_LOCATION_VIP_URL = "/host/server/location/vip/cache.json";
    public static final String API_RECOMMEND_URL = "/data/recommend.json?start=%s&limit=20";
    public static final String API_CONNLOG_URL = "/monitor/connlog.json";
    public static final String API_LOGIN_URL = "/user/login.json";
    public static final String API_REG_URL = "/user/reg.json";
    public static final String API_LOGOUT_URL = "/user/logout.json";
    public static final int connTimeOut = 20;
    public static final VpnType vpnType = VpnType.IKEV2_EAP;
    public static final String NET_ERROR = "network error";
    public static final String CONFIG_PARAM = "CONFIG_PARAM";
    public static final String TITLE = "TITLE";
    public static final String VPN_STATUS = "VPN_STATUS";
    public static final String LOCATION_ICON_ALL ="timeline://img/flag_all.png";
    public static String BASE_IP = "api.sspacee.com";
    public static String ABOUT_ZH = "http://file.sspacee.com/file/html/about_zh.html";
    public static String ABOUT = "http://file.sspacee.com/file/html/about.html";
    public static List<String> colorBg = Arrays.asList("#552d5d82", "#55135689", "#552292e9", "#5583878b", "#7f8d8f45", "#ffc49924", "#ff83713f", "#ff569b2b", "#ff882b9b");

    public static String getUrl(String uri) {
        return "http://" + BASE_IP + "/vpn/api" + uri;
    }

    public static String getUrlWithParam(String url, Object... param) {
        return String.format(getUrl(url), param);
    }
    public static final String LOCATION_CHOOSE="LOCATION_CHOOSE";
    public static class VpnTypeLevel{
        public static final int LOCATION_TYPE_FREE = 0;
        public static final int LOCATION_TYPE_VIP = 1;
        public static final int LOCATION_TYPE_VIP2 = 2;
        public static final int LOCATION_TYPE_VIP3 = 3;
        public static final int LOCATION_TYPE_VIP4 = 4;
    }
    public static class ShowType {
        public static final int Normal = 0;
        public static final int Blur = 1;
        public static final int Text = 2;
        public static final int Text_Below = 3;
    }
    public static class NetWork{
        public static String UA_DEFAULT = null;
        public static String UA_APP_SUFFIX = null;

        static {
            UA_DEFAULT = System.getProperty("http.agent", "");
        }
        public static String uc = null;
        public static String CHARSET = "utf-8";
    }
    public static final int UM_INTERVAL = 40;
    public static final String LANG_ZH = "zh";
    public static final String LANG_US = "en";
    public static final String LOG_FILE = "vpn.log";
    public static final String LOG_FILE_FOR_UPLOAD = "vpn_up.log";
    public static final String BUG_FILE = "log.log";
    public static final String BUG_FILE_FOR_UPLOAD = "log_up.log";
    public static boolean DEBUG = false;
    public static Map<String, Integer> img = new HashMap<>();

    static {
        img.put("flag_ch", R.drawable.flag_ch);
        img.put("flag_af", R.drawable.flag_af);
        img.put("flag_au", R.drawable.flag_au);
        img.put("flag_be", R.drawable.flag_be);
        img.put("flag_br", R.drawable.flag_br);
        img.put("flag_ca", R.drawable.flag_ca);
        img.put("flag_cn", R.drawable.flag_cn);
        img.put("flag_de", R.drawable.flag_de);
        img.put("flag_es", R.drawable.flag_es);
        img.put("flag_eu", R.drawable.flag_eu);
        img.put("flag_fi", R.drawable.flag_fi);
        img.put("flag_fr", R.drawable.flag_fr);
        img.put("flag_gb", R.drawable.flag_gb);
        img.put("flag_gr", R.drawable.flag_gr);
        img.put("flag_hk", R.drawable.flag_hk);
        img.put("flag_in", R.drawable.flag_in);
        img.put("flag_jp", R.drawable.flag_jp);
        img.put("flag_kr", R.drawable.flag_kr);
        img.put("flag_ly", R.drawable.flag_ly);
        img.put("flag_my", R.drawable.flag_my);
        img.put("flag_nl", R.drawable.flag_nl);
        img.put("flag_pl", R.drawable.flag_pl);
        img.put("flag_ro", R.drawable.flag_ro);
        img.put("flag_ru", R.drawable.flag_ru);
        img.put("flag_se", R.drawable.flag_se);
        img.put("flag_sg", R.drawable.flag_sg);
        img.put("flag_tr", R.drawable.flag_tr);
        img.put("flag_tw", R.drawable.flag_tw);
        img.put("flag_uns", R.drawable.flag_uns);
        img.put("flag_us", R.drawable.flag_us);
        img.put("flag_vn", R.drawable.flag_vn);
        img.put("flag_all", R.drawable.flag_all);
    }

}
