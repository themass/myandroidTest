// Copyright 2020 Bytedance Inc. All Rights Reserved.
// Author: fengkai.0518@bytedance.com (fengkai.0518)

package com.openapi.commons.common.asr;


import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import com.bytedance.speech.speechengine.SpeechEngine;
import com.bytedance.speech.speechengine.SpeechEngineDefines;
import com.bytedance.speech.speechengine.SpeechEngineGenerator;
import com.openapi.commons.common.asr.utils.SensitiveDefines;
import com.openapi.commons.common.util.FileUtils;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.ks.myapp.base.MyApplication;

import chat.ui.data.model.MsgContent;

public class AsrByteDance implements SpeechEngine.SpeechListener {

    private SpeechEngine mSpeechEngine = null;
    HandlerThread handlerThread = new HandlerThread("BackgroundThread");
    Handler handler;
    private AsrCallBackListener asrCallBackListener;

    @Override
    public void onSpeechMessage(int type, byte[] data, int len) {
        String stdData = new String(data);
        switch (type) {
            case SpeechEngineDefines.MESSAGE_TYPE_ENGINE_START:
                // Callback: 引擎启动成功回调
                LogUtil.i("Callback: 引擎启动成功: data: " + stdData);
                asrCallBackListener.speechStart(stdData);
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_ENGINE_STOP:
                // Callback: 引擎关闭回调
                LogUtil.i("Callback: 引擎关闭: data: " + stdData);
                asrCallBackListener.speechStop();
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_ENGINE_ERROR:
                // Callback: 错误信息回调
                LogUtil.i("Callback: 错误信息: " + stdData);
//                speechError(stdData);
                asrCallBackListener.speechAsrResult("......", true);
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_CONNECTION_CONNECTED:
//                LogUtil.i("Callback: 建连成功: data: " + stdData);
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_PARTIAL_RESULT:
                // Callback: ASR 当前请求的部分结果回调
//                LogUtil.i("Callback: ASR 当前请求的部分结果:" + stdData);
                asrCallBackListener.speechAsrResult(stdData, false);
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_FINAL_RESULT:
                // Callback: ASR 当前请求最终结果回调
                LogUtil.i("Callback: ASR 当前请求最终结果:" + stdData);
                asrCallBackListener.speechAsrResult(stdData, true);
                stopEngine();
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_VOLUME_LEVEL:
                // Callback: 录音音量回调
//                LogUtil.i("Callback: 录音音量");
                break;
            default:
                break;
        }
    }

    public void initEngine(Context context, AsrCallBackListener listener) {
        SpeechEngineGenerator.PrepareEnvironment(context.getApplicationContext(), MyApplication.getInstance());
        this.asrCallBackListener = listener;
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        if (mSpeechEngine == null) {
            mSpeechEngine = SpeechEngineGenerator.getInstance();
            mSpeechEngine.createEngine();
            mSpeechEngine.setContext(MyApplication.getInstance());
            mSpeechEngine.setListener(this);
        }
    }
    int ret = SpeechEngineDefines.ERR_ADDRESS_INVALID;
    public void sendEngine(Context context) {
        if(ret != SpeechEngineDefines.ERR_NO_ERROR) {
            handler.post(() -> {
                LogUtil.i("SDK 版本号: " + mSpeechEngine.getVersion());
                LogUtil.i("配置初始化参数.");
                configInitParams();
                LogUtil.i("引擎初始化.");
                ret = mSpeechEngine.initEngine();
                if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                    String errMessage = "初始化失败，返回值: " + ret;
                    LogUtil.e(errMessage);
                    return;
                }
                LogUtil.i("初始化完成");
            });
        }
    }

    private void configInitParams() {
        //【必需配置】Engine Name
        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ENGINE_NAME_STRING, SpeechEngineDefines.ASR_ENGINE);

        //【可选配置】Debug & Log
        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_DEBUG_PATH_STRING, FileUtils.getWriteFilePath(MyApplication.getInstance()));
        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_LOG_LEVEL_STRING, SpeechEngineDefines.LOG_LEVEL_INFO);

//        【可选配置】User ID（用以辅助定位线上用户问题）
        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_UID_STRING, SensitiveDefines.UID);

        //【必需配置】配置音频来源
        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_RECORDER_TYPE_STRING, SpeechEngineDefines.RECORDER_TYPE_FILE);

//            //【可选配置】录音文件保存路径，如配置，SDK会将录音保存到该路径下，文件格式为 .wav
//        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_REC_PATH_STRING, FileUtils.getWriteFilePath(MyApplication.getInstance()));

        //【可选配置】音频采样率，默认16000
        mSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_SAMPLE_RATE_INT, 16000);
        //【可选配置】音频通道数，默认1，可选1或2
//        mSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_CHANNEL_NUM_INT, mSettings.getInt(R.string.config_channel));
//        //【可选配置】上传给服务的音频通道数，默认1，可选1或2，一般与PARAMS_KEY_CHANNEL_NUM_INT保持一致即可
//        mSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_UP_CHANNEL_NUM_INT, mSettings.getInt(R.string.config_channel));

        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_ADDRESS_STRING, SensitiveDefines.DEFAULT_ADDRESS);
        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_URI_STRING, SensitiveDefines.ASR_DEFAULT_URI);

        String appid = SensitiveDefines.APPID;
        //【必需配置】鉴权相关：Appid
        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_APP_ID_STRING, appid);

        String token = SensitiveDefines.TOKEN;
        //【必需配置】鉴权相关：Token
        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_APP_TOKEN_STRING, token);

        String cluster = SensitiveDefines.ASR_DEFAULT_CLUSTER;
        //【必需配置】识别服务所用集群
        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_CLUSTER_STRING, cluster);

        //【可选配置】在线请求的建连与接收超时，一般不需配置使用默认值即可
        mSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_ASR_CONN_TIMEOUT_INT, 3000);
        mSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_ASR_RECV_TIMEOUT_INT, 5000);

//        //【可选配置】在线请求断连后，重连次数，默认值为0，如果需要开启需要设置大于0的次数
//        mSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_ASR_MAX_RETRY_TIMES_INT, mSettings.getInt(R.string.config_asr_max_retry_times));
    }

    public void sendEngine(String filePath, MsgContent msgContent) {
        handler.post(() -> {
            LogUtil.i("配置启动参数.");
            configStartAsrParams();
            mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_RECORDER_FILE_STRING, filePath);
            // Directive：启动引擎前调用SYNC_STOP指令，保证前一次请求结束。
            int ret = mSpeechEngine.sendDirective(SpeechEngineDefines.DIRECTIVE_SYNC_STOP_ENGINE, "");
            if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                LogUtil.e("send directive syncstop failed, " + ret);
            } else {
                LogUtil.i("启动引擎");
                LogUtil.i("Directive: DIRECTIVE_START_ENGINE");
                ret = mSpeechEngine.sendDirective(SpeechEngineDefines.DIRECTIVE_START_ENGINE, msgContent.getMsgId());
                if (ret == SpeechEngineDefines.ERR_REC_CHECK_ENVIRONMENT_FAILED) {
                } else if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                    LogUtil.i("send directive start failed, " + ret);
                }
            }
        });

    }

    public void stopEngine() {
        handler.post(() -> {
            mSpeechEngine.sendDirective(SpeechEngineDefines.DIRECTIVE_STOP_ENGINE, "");
        });
    }

    private void configStartAsrParams() {
        //【可选配置】是否开启顺滑(DDC)
        mSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ASR_ENABLE_DDC_BOOL, true);
        //【可选配置】是否开启文字转数字(ITN)
        mSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ASR_ENABLE_ITN_BOOL, true);
        //【可选配置】是否开启标点
        mSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ASR_SHOW_NLU_PUNC_BOOL, true);
        //【可选配置】设置识别语种
        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_LANGUAGE_STRING, "en-US");
        //【可选配置】是否启用云端自动判停
        mSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ASR_AUTO_STOP_BOOL, true);
        //【可选配置】是否隐藏句尾标点
        mSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ASR_DISABLE_END_PUNC_BOOL, false);

        //【可选配置】控制识别结果返回的形式，全量返回或增量返回，默认为全量
//        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_RESULT_TYPE_STRING, mSettings.getOptionsValue(R.string.config_asr_result_type, this));

        //【可选配置】设置VAD头部静音时长，用户多久没说话视为空音频，即静音检测时长
//        mSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_ASR_VAD_START_SILENCE_TIME_INT, mSettings.getInt(R.string.config_asr_vad_start_silence_time));
        //【可选配置】设置VAD尾部静音时长，用户说话后停顿多久视为说话结束，即自动判停时长
//        mSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_ASR_VAD_END_SILENCE_TIME_INT, mSettings.getInt(R.string.config_asr_vad_end_silence_time));
        //【可选配置】设置VAD模式，用于定制VAD场景，默认为空
//        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_VAD_MODE_STRING, mSettings.getString(R.string.config_asr_vad_mode));
        //【可选配置】用户音频输入最大时长，仅一句话识别场景生效，单位毫秒，默认为 60000ms.
//        mSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_VAD_MAX_SPEECH_DURATION_INT, mSettings.getInt(R.string.config_vad_max_speech_duration));

        //【可选配置】控制是否返回录音音量，在 APP 需要显示音频波形时可以启用
        mSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ENABLE_GET_VOLUME_BOOL, true);

        //【可选配置】设置纠错词表，识别结果会根据设置的纠错词纠正结果，例如："{\"古爱玲\":\"谷爱凌\"}"，当识别结果中出现"古爱玲"时会替换为"谷爱凌"
//        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_CORRECT_WORDS_STRING, mSettings.getString(R.string.config_asr_correct_words));


//        //【可选配置】更新 ASR 热词
//        if (!mSettings.getString(R.string.config_asr_hotwords).isEmpty()) {
//            Log.d(SpeechDemoDefines.TAG, "Set hotwords.");
//            setHotWords(mSettings.getString(R.string.config_asr_hotwords));
//        }
    }

    public static interface AsrCallBackListener {
        public void speechAsrResult(String data, boolean isFinish);

        public void speechStart(String id);

        public void speechStop();
    }
}
