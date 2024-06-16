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


    // AU
    public static final String AU_DEFAULT_URI = "/api/v1/sauc";
    public static final String AU_DEFAULT_CLUSTER = "YOUR AU CLUSTER";

    // TTS
    public static final String TTS_DEFAULT_URI = "/api/v1/tts/ws_binary";
    public static final String TTS_DEFAULT_CLUSTER = "YOUR TTS CLUSTER";
    public static final String TTS_DEFAULT_BACKEND_CLUSTER = "YOUR TTS BACKEND CLUSTER";
    public static final String TTS_DEFAULT_ONLINE_VOICE = "TTS ONLINE VOICE";
    public static final String TTS_DEFAULT_ONLINE_VOICE_TYPE = "TTS ONLINE VOICE TYPE";
    public static final String TTS_DEFAULT_OFFLINE_VOICE = "TTS OFFLINE VOICE";
    public static final String TTS_DEFAULT_OFFLINE_VOICE_TYPE = "TTS OFFLINE VOICE TYPE";
    public static final String TTS_DEFAULT_ONLINE_LANGUAGE = "TTS ONLINE LANGUAGE";
    public static final String TTS_DEFAULT_OFFLINE_LANGUAGE = "TTS OFFLINE LANGUAGE";
    public static final String[] TTS_DEFAULT_DOWNLOAD_OFFLINE_VOICES = new String[]{};

    // VoiceClone
    public static final String VOICECLONE_DEFAULT_UIDS = "uid_1;uid_2";
    public static final int VOICECLONE_DEFAULT_TASK_ID = -1;

    // VoiceConv
    public static final String VOICECONV_DEFAULT_URI = "/api/v1/voice_conv/ws";
    public static final String VOICECONV_DEFAULT_CLUSTER = "YOUR VOICECONV CLUSTER";
    public static final String VOICECONV_DEFAULT_VOICE = "VOICECONV VOICE";
    public static final String VOICECONV_DEFAULT_VOICE_TYPE = "VOICECONV VOICE TYPE";

    // Fulllink
    public static final String FULLLINK_DEFAULT_URI = "FULLLINK URI";

    // Dialog
    public static final String DIALOG_DEFAULT_URI = "DIALOG URI";
    public static final String DIALOG_DEFAULT_APP_ID = "DIALOG APP ID";
    public static final String DIALOG_DEFAULT_ID = "DIALOG ID";
    public static final String DIALOG_DEFAULT_ROLE = "DIALOG ROLE";
    public static final String DIALOG_DEFAULT_CLOTHES_TYPE = "DIALOG CLOTHES TYPE";
    public static final String DIALOG_DEFAULT_TTA_VOICE_TYPE = "DIALOG TTA_VOICE_TYPE";

    // CAPT
    public static final String CAPT_DEFAULT_MDD_URI = "CAPT MDD URI";
    public static final String CAPT_DEFAULT_CLUSTER = "YOUR CAPT CLUSTER";

}
