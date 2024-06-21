package com.openapi.commons.common.asr.utils;

import android.content.Context;

import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.constant.Constants;

/**
 * SensitiveDefines
 * Defines in this class should be different for different business,
 * please contact with @Bytedance AILab about what value should be set before use it.
 */
public class SensitiveDefines {

    // User Info
    public static final String UID = "test";

    // Device Info
    public static final String DID = "mytest";

    // Online & Resource Authorization
    private static final String APPID = "4966581804";
    private static final String TOKEN = "Bearer;yT2m2hVfJjhy79G6MfTB4Jv3X7JRpGs7";
    private static final String ASR_DEFAULT_CLUSTER = "volcengine_input_common";
    private static final String TTS_DEFAULT_CLUSTER = "volcano_tts";
    private static final String DEFUALT_VOICE="BV001_streaming";
    public static final String DEFUALT_VOICE_USER="通用女声";

    public static String getAppId(Context context){
        String v = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.ASR_APPID, APPID);
        if(!StringUtils.isEmpty(v)){
            return v;
        }
        return APPID;
    }
    public static String getAppToken(Context context){
        String v = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.ASR_TOKEN, TOKEN);
        if(!StringUtils.isEmpty(v)){
            return v;
        }
        return TOKEN;
    }
    public static String getAppCluster(Context context){
        String v = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.ASR_DEFAULT_CLUSTER, ASR_DEFAULT_CLUSTER);
        if(!StringUtils.isEmpty(v)){
            return v;
        }
        return ASR_DEFAULT_CLUSTER;
    }
    public static String getTtsAppCluster(Context context){
        String v = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.TTS_DEFAULT_CLUSTER, TTS_DEFAULT_CLUSTER);
        if(!StringUtils.isEmpty(v)){
            return v;
        }
        return TTS_DEFAULT_CLUSTER;
    }
    public static String geTtsVoice(Context context){
        String v = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.TTS_DEFAULT_VOICE, DEFUALT_VOICE);
        if(!StringUtils.isEmpty(v)){
            return v;
        }
        return DEFUALT_VOICE;
    }


    public static final String APP_VERSION = "YOUR APP VERSION";

    // Offline Authorization
    public static final String AUTHENTICATE_ADDRESS = "AUTHENTICAT ADDRESS";
    public static final String AUTHENTICATE_URI = "AUTHENTICATE URI";
    public static final String LICENSE_NAME = "YOUR LICENSE NAME";
    public static final String LICENSE_BUSI_ID = "YOUR LICENSE BUSI_ID";
    public static final String SECRET = "YOUR SECRET";
    public static final String BUSINESS_KEY = "YOUR BUSINESS KEY";

    // Address
    public static final String DEFAULT_ADDRESS = "wss://openspeech.bytedance.com";
    public static final String DEFAULT_HTTP_ADDRESS = "https://openspeech.bytedance.com";

    // ASR
    public static final String ASR_DEFAULT_URI = "/api/v2/asr";

    // TTS
    public static final String TTS_DEFAULT_URI = "/api/v1/tts/ws_binary";
    // VoiceClone
    public static final String VOICECLONE_DEFAULT_UIDS = "uid_1;uid_2";
    public static final int VOICECLONE_DEFAULT_TASK_ID = -1;

}
