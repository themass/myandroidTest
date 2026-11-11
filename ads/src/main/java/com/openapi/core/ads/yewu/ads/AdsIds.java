package com.openapi.core.ads.yewu.ads;

import com.openapi.core.ads.yewu.ads.adview.AdviewConstant;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdsIds {


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


    public static Map<String, String> adviewToAdmobBanner = new HashMap<>();

    static {
        adviewToAdmobBanner.put(AdviewConstant.ADS_ADVIEW_KEY1, ADMOB_BANNER_ID2);
        adviewToAdmobBanner.put(AdviewConstant.ADS_ADVIEW_KEY2, ADMOB_BANNER_ID);
        adviewToAdmobBanner.put(AdviewConstant.ADS_ADVIEW_KEY, ADMOB_BANNER_ID);
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
