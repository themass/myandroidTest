package com.openai.ads.yewu.ads;

import com.openai.ads.yewu.ads.adview.AdviewConstant;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Constants {


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


    public static final String ADMOB_REWARD_ID_TEST = "ca-app-pub-3940256099942544/5224354917";
    public static final String ADMOB_REWARD_ID = "ca-app-pub-6599725010915516/5135784679";

    public static final String ADMOB_BANNER_ID = "ca-app-pub-6599725010915516/4233766548";
    public static final String ADMOB_BANNER_ID2 = "ca-app-pub-6599725010915516/6148132379";

    public static final String ADMOB_INTER_ID = "ca-app-pub-6599725010915516/9330697473";
    public static final String ADMOB_INTER_ID_TEST = "ca-app-pub-3940256099942544/1033173712";

    public static final String ADMOB_SPLASH_ID = "ca-app-pub-6599725010915516/3521969031";
    public static final String ADMOB_SPLASH_ID_TEST = "ca-app-pub-3940256099942544/3419835294";


    public static final String ADMOB_REWARD_UNIT_ID = "ca-app-pub-7849865307083433/1108754074";
    public static final String ADMOB_REWARD_UNIT_ID2 = "ca-app-pub-7849865307083433/1842540805";
    public static final String ADMOB_REWARD_UNIT_ID3 = "ca-app-pub-7849865307083433/5020078921";
    public static final List<String> ADMOB_REWARD_UNIT_IDS = Arrays.asList(ADMOB_REWARD_UNIT_ID, ADMOB_REWARD_UNIT_ID, ADMOB_REWARD_UNIT_ID);


    public static final String Mob_APPID_TEST = "144002";
    public static final String Mob_APPKEY_TEST = "7c22942b749fe6a6e361b675e96b3ee9";
    public static final String Mob_SPLASH_UNIT_TEST = "328916";
    public static final String Mob_SPLASH_UNIT_PLACE_TEST = "1542060";
    public static final String Mob_INTER_UNIT_TEST = "290653";
    public static final String Mob_INTER_UNIT_PLACE_TEST = "462374";
    public static final String Mob_REWARD_UNIT_TEST = "290651";
    public static final String Mob_REWARD_UNIT_PLACE_TEST = "462372";

    public static final String Mob_UNIT_BANNER_PLACE_TEST = "290655";
    public static final String Mob_UNIT_BANNER1_TEST = "462376";
    public static final String Mob_UNIT_BANNER2_TEST = "462376";
    public static final String Mob_UNIT_BANNER3_TEST = "462376";
    public static final String Mob_UNIT_BANNER_TEST = "462376";

    public static final String Mob_APPID = "209825";
    public static final String Mob_APPKEY = "f1f0a8637166a0459402b9de3c88a93f";
    public static final String Mob_SPLASH_UNIT = "809686";
    public static final String Mob_SPLASH_UNIT_PLACE = "2392263";
    public static final String Mob_INTER_UNIT = "809698";
    public static final String Mob_INTER_UNIT_PLACE = "2392276";
    public static final String Mob_REWARD_UNIT = "809687";
    public static final String Mob_REWARD_UNIT_PLACE = "2392264";

    public static final String Mob_UNIT_BANNER_PLACE = "811116";
    public static final String Mob_UNIT_BANNER1 = "2394073";
    public static final String Mob_UNIT_BANNER2 = "2394071";
    public static final String Mob_UNIT_BANNER3 = "2394069";
    public static final String Mob_UNIT_BANNER = "2394067";

    public static final String Mob_UNIT_WALL = "88840";

    public static final String FACEBOOK_BANNER_ID = "1326501941825966_1326517011824459";
    public static final String FACEBOOK_INTER_ID = "1326501941825966_1326559621820198";

    public static Map<String, String> adviewToMobvBanner = new HashMap<>();

    static {
        adviewToMobvBanner.put(AdviewConstant.ADS_ADVIEW_KEY1, Mob_UNIT_BANNER1);
        adviewToMobvBanner.put(AdviewConstant.ADS_ADVIEW_KEY2, Mob_UNIT_BANNER2);
        adviewToMobvBanner.put(AdviewConstant.ADS_ADVIEW_KEY, Mob_UNIT_BANNER);
    }

    public static Map<String, String> adviewToAdmobBanner = new HashMap<>();

    static {
        adviewToAdmobBanner.put(AdviewConstant.ADS_ADVIEW_KEY1, ADMOB_BANNER_ID2);
        adviewToAdmobBanner.put(AdviewConstant.ADS_ADVIEW_KEY2, ADMOB_BANNER_ID);
        adviewToAdmobBanner.put(AdviewConstant.ADS_ADVIEW_KEY, ADMOB_BANNER_ID);
    }

    public static Map<String, String> adviewToFacebookBanner = new HashMap<>();

    static {
        adviewToFacebookBanner.put(AdviewConstant.ADS_ADVIEW_KEY1, FACEBOOK_BANNER_ID);
        adviewToFacebookBanner.put(AdviewConstant.ADS_ADVIEW_KEY2, FACEBOOK_BANNER_ID);
        adviewToFacebookBanner.put(AdviewConstant.ADS_ADVIEW_KEY, FACEBOOK_BANNER_ID);
    }

    public static final int ADS_REWARD_SHOW_CLICK = 100;
    public static final String SCORE_CLICK_CLICK = "SCORE_CLICK_CLICK";
    public static int PROBABILITY = 3;

    public static final int ADS_SHOW_SCORE = 10;
    public static final int ADS_SHOW_CLICK = 30;
    public static final int ADS_SHOW_VIDEO_SCORE = 30;
    public static final int maxRate = 10;
    public static final String SCORE_CLICK = "SCORE_CLICK";
    public static final long SCORE_CLICK_INTERVAL = 2;
}
