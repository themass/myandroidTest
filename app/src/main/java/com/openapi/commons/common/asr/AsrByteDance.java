// Copyright 2020 Bytedance Inc. All Rights Reserved.
// Author: fengkai.0518@bytedance.com (fengkai.0518)

package com.openapi.commons.common.asr;


import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.bytedance.speech.speechengine.SpeechEngine;
import com.bytedance.speech.speechengine.SpeechEngineDefines;
import com.bytedance.speech.speechengine.SpeechEngineGenerator;
import com.openapi.commons.common.asr.utils.SensitiveDefines;
import com.openapi.commons.common.util.FileUtils;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.ks.myapp.base.MyApplication;

import chat.ui.data.model.MsgContent;

public class AsrByteDance implements SpeechEngine.SpeechListener {

    private SpeechEngine mAsrSpeechEngine = null;
    private SpeechEngine mTtsSpeechEngine = null;
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
            case SpeechEngineDefines.MESSAGE_TYPE_TTS_SYNTHESIS_BEGIN:
                // Callback: 合成开始回调
                LogUtil.i("Callback: TTS 合成开始:"+stdData);
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_TTS_SYNTHESIS_END:
                // Callback: 合成结束回调
                LogUtil.i("Callback: TTS 合成结束"+stdData);
                asrCallBackListener.callBackTtsData(stdData);
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_TTS_START_PLAYING:
                // Callback: 播放开始回调
                LogUtil.i("Callback: TTS 播放开始");
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_TTS_PLAYBACK_PROGRESS:
                // Callback: 播放进度回调
                LogUtil.i("Callback: TTS 播放进度");
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_TTS_FINISH_PLAYING:
                // Callback: 播放结束回调
                LogUtil.i("Callback: TTS 播放结束:" );
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_TTS_AUDIO_DATA:
                // Callback: 音频数据回调
                LogUtil.i("Callback: TTS "+String.format("Callback: 音频数据，长度 %d 字节", stdData.length()));
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
        if (mAsrSpeechEngine == null) {
            mAsrSpeechEngine = SpeechEngineGenerator.getInstance();
            mAsrSpeechEngine.createEngine();
            mAsrSpeechEngine.setContext(MyApplication.getInstance());
            mAsrSpeechEngine.setListener(this);
        }
        if (mTtsSpeechEngine == null) {
            mTtsSpeechEngine = SpeechEngineGenerator.getInstance();
            mTtsSpeechEngine.createEngine();
            mTtsSpeechEngine.setContext(MyApplication.getInstance());
            mTtsSpeechEngine.setListener(this);
        }
    }
    int ret = SpeechEngineDefines.ERR_ADDRESS_INVALID;
    public void initAsrEngine(Context context) {
        if(ret != SpeechEngineDefines.ERR_NO_ERROR) {
            handler.post(() -> {
                LogUtil.i("SDK 版本号: " + mAsrSpeechEngine.getVersion());
                LogUtil.i("配置初始化参数.");
                configAsrInitParams();
                LogUtil.i("引擎初始化.");
                ret = mAsrSpeechEngine.initEngine();
                if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                    String errMessage = "初始化失败，返回值: " + ret;
                    LogUtil.e(errMessage);
//                    return;
                }
                LogUtil.i("初始化完成");

                LogUtil.i("ttsSDK 版本号: " + mTtsSpeechEngine.getVersion());
                LogUtil.i("tts配置初始化参数.");
                configTtsInitParams();
                LogUtil.i("tts引擎初始化.");
                retTts = mTtsSpeechEngine.initEngine();
                if (retTts != SpeechEngineDefines.ERR_NO_ERROR) {
                    String errMessage = "tts初始化失败，返回值: " + retTts;
                    LogUtil.e(errMessage);
                    return;
                }
                LogUtil.i("tts初始化完成");
            });
        }
    }
    int retTts = SpeechEngineDefines.ERR_ADDRESS_INVALID;
    public void initTtsEngine(Context context) {
        if(retTts != SpeechEngineDefines.ERR_NO_ERROR) {
            handler.post(() -> {

            });
        }
    }
    private void configTtsInitParams() {
        mTtsSpeechEngine.setOptionString( SpeechEngineDefines.PARAMS_KEY_ENGINE_NAME_STRING, SpeechEngineDefines.TTS_ENGINE);
        //【可选配置】Debug & Log
        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_DEBUG_PATH_STRING, FileUtils.getWriteFilePath(MyApplication.getInstance()));
        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_LOG_LEVEL_STRING, SpeechEngineDefines.LOG_LEVEL_INFO);
        //        【可选配置】User ID（用以辅助定位线上用户问题）
//        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_UID_STRING, SensitiveDefines.UID);
        //【可选配置】是否将合成出的音频保存到设备上，为 true 时需要正确配置 PARAMS_KEY_TTS_AUDIO_PATH_STRING 才会生效
        // PARAMS_KEY_TTS_ENABLE_DUMP_BOOL 配置为 true 的音频时为【必需配置】，否则为【可选配置】
        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_AUDIO_PATH_STRING, FileUtils.getWriteFilePath(MyApplication.getInstance()));
        mTtsSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_TTS_ENABLE_DUMP_BOOL, true);
        // TTS 音频文件保存目录，必须在合成之前创建好且 APP 具有访问权限，保存的音频文件名格式为 tts_{reqid}.wav, {reqid} 是本次合成的请求 id
        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_UID_STRING, SensitiveDefines.UID);
        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_DEVICE_ID_STRING, SensitiveDefines.DID);

        //【可选配置】合成出的音频的采样率，默认为 24000
//        mTtsSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_SAMPLE_RATE_INT, 24000);
        //【可选配置】打断播放时使用多长时间淡出停止，单位：毫秒。默认值 0 表示不淡出
//        mTtsSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_AUDIO_FADEOUT_DURATION_INT, 0);
        //【必需配置】鉴权相关：Appid
        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_APP_ID_STRING, SensitiveDefines.getAppId(MyApplication.getInstance()));
        //【必需配置】鉴权相关：Token
        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_APP_TOKEN_STRING, SensitiveDefines.getAppToken(MyApplication.getInstance()));
        //【必需配置】识别服务所用集群
        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_CLUSTER_STRING, SensitiveDefines.getTtsAppCluster(MyApplication.getInstance()));

        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_ADDRESS_STRING, SensitiveDefines.DEFAULT_ADDRESS);
        //【必需配置】语音合成服务Uri
        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_URI_STRING, SensitiveDefines.TTS_DEFAULT_URI);
        //【必需配置】语音合成服务所用集群
        //【可选配置】在线合成下发的 opus-ogg 音频的压缩倍率
//        mTtsSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_COMPRESSION_RATE_INT, 10);
    }
    private void configAsrInitParams() {
        //【必需配置】Engine Name
        mAsrSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ENGINE_NAME_STRING, SpeechEngineDefines.ASR_ENGINE);

        //【可选配置】Debug & Log
        mAsrSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_DEBUG_PATH_STRING, FileUtils.getWriteFilePath(MyApplication.getInstance()));
        mAsrSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_LOG_LEVEL_STRING, SpeechEngineDefines.LOG_LEVEL_INFO);

//        【可选配置】User ID（用以辅助定位线上用户问题）
        mAsrSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_UID_STRING, SensitiveDefines.UID);

        //【必需配置】配置音频来源
        mAsrSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_RECORDER_TYPE_STRING, SpeechEngineDefines.RECORDER_TYPE_FILE);

//            //【可选配置】录音文件保存路径，如配置，SDK会将录音保存到该路径下，文件格式为 .wav
//        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_REC_PATH_STRING, FileUtils.getWriteFilePath(MyApplication.getInstance()));

        //【可选配置】音频采样率，默认16000
        mAsrSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_SAMPLE_RATE_INT, 16000);
        //【可选配置】音频通道数，默认1，可选1或2
//        mSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_CHANNEL_NUM_INT, mSettings.getInt(R.string.config_channel));
//        //【可选配置】上传给服务的音频通道数，默认1，可选1或2，一般与PARAMS_KEY_CHANNEL_NUM_INT保持一致即可
//        mSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_UP_CHANNEL_NUM_INT, mSettings.getInt(R.string.config_channel));

        mAsrSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_ADDRESS_STRING, SensitiveDefines.DEFAULT_ADDRESS);
        mAsrSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_URI_STRING, SensitiveDefines.ASR_DEFAULT_URI);

        //【必需配置】鉴权相关：Appid
        mAsrSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_APP_ID_STRING, SensitiveDefines.getAppId(MyApplication.getInstance()));

        //【必需配置】鉴权相关：Token
        mAsrSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_APP_TOKEN_STRING, SensitiveDefines.getAppToken(MyApplication.getInstance()));

        //【必需配置】识别服务所用集群
        mAsrSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_CLUSTER_STRING, SensitiveDefines.getAppCluster(MyApplication.getInstance()));


        //【可选配置】在线请求的建连与接收超时，一般不需配置使用默认值即可
        mAsrSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_ASR_CONN_TIMEOUT_INT, 3000);
        mAsrSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_ASR_RECV_TIMEOUT_INT, 5000);

//        //【可选配置】在线请求断连后，重连次数，默认值为0，如果需要开启需要设置大于0的次数
//        mSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_ASR_MAX_RETRY_TIMES_INT, mSettings.getInt(R.string.config_asr_max_retry_times));
    }

    public void sendEngine(String filePath, MsgContent msgContent) {
        handler.post(() -> {
            LogUtil.i("配置启动参数.");
            configStartAsrParams();
            mAsrSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_RECORDER_FILE_STRING, filePath);
            // Directive：启动引擎前调用SYNC_STOP指令，保证前一次请求结束。
            int ret = mAsrSpeechEngine.sendDirective(SpeechEngineDefines.DIRECTIVE_SYNC_STOP_ENGINE, "");
            if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                LogUtil.e("send directive syncstop failed, " + ret);
            } else {
                LogUtil.i("启动引擎");
                LogUtil.i("Directive: DIRECTIVE_START_ENGINE");
                ret = mAsrSpeechEngine.sendDirective(SpeechEngineDefines.DIRECTIVE_START_ENGINE, msgContent.getMsgId());
                if (ret == SpeechEngineDefines.ERR_REC_CHECK_ENVIRONMENT_FAILED) {
                } else if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                    LogUtil.i("send directive start failed, " + ret);
                }
            }
        });

    }

    public void stopEngine() {
        handler.post(() -> {
            mAsrSpeechEngine.sendDirective(SpeechEngineDefines.DIRECTIVE_STOP_ENGINE, "");
            mTtsSpeechEngine.sendDirective(SpeechEngineDefines.DIRECTIVE_STOP_ENGINE, "");
        });
    }

    private void configStartAsrParams() {
        //【可选配置】是否开启顺滑(DDC)
        mAsrSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ASR_ENABLE_DDC_BOOL, true);
        //【可选配置】是否开启文字转数字(ITN)
        mAsrSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ASR_ENABLE_ITN_BOOL, true);
        //【可选配置】是否开启标点
        mAsrSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ASR_SHOW_NLU_PUNC_BOOL, true);
        //【可选配置】设置识别语种
        mAsrSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ASR_LANGUAGE_STRING, "en-US");
        //【可选配置】是否启用云端自动判停
        mAsrSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ASR_AUTO_STOP_BOOL, true);
        //【可选配置】是否隐藏句尾标点
        mAsrSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ASR_DISABLE_END_PUNC_BOOL, false);
        //【可选配置】控制是否返回录音音量，在 APP 需要显示音频波形时可以启用
        mAsrSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ENABLE_GET_VOLUME_BOOL, true);

    }


    public void sendTtsEngine(String  text) {
        handler.post(() -> {
            LogUtil.i("tts配置启动参数.");
            configStartTtsParams();
            // Directive：启动引擎前调用SYNC_STOP指令，保证前一次请求结束。
            mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_TEXT_STRING, text);
            int ret = mTtsSpeechEngine.sendDirective(SpeechEngineDefines.DIRECTIVE_SYNC_STOP_ENGINE, "");
            if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                LogUtil.e("tts send directive syncstop failed, " + ret);
            } else {
                LogUtil.i("tts启动引擎");
                LogUtil.i("Directive: DIRECTIVE_START_ENGINE");
                ret = mTtsSpeechEngine.sendDirective(SpeechEngineDefines.DIRECTIVE_START_ENGINE, "111");

                if (ret == SpeechEngineDefines.ERR_REC_CHECK_ENVIRONMENT_FAILED) {
                } else if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                    LogUtil.i("send directive start failed, " + ret);
                }
            }
        });

    }

    private void configStartTtsParams() {
        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_SCENARIO_STRING, SpeechEngineDefines.TTS_SCENARIO_TYPE_NORMAL);
// 合成策略：在线合成
        mTtsSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_WORK_MODE_INT, SpeechEngineDefines.TTS_WORK_MODE_ONLINE);
// 建连超时，默认12000ms
        mTtsSpeechEngine.setOptionInt( SpeechEngineDefines.PARAMS_KEY_TTS_CONN_TIMEOUT_INT, 12000);
// 接收超时，默认8000ms
        mTtsSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_RECV_TIMEOUT_INT, 8000);
        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_TEXT_TYPE_STRING, SpeechEngineDefines.TTS_TEXT_TYPE_PLAIN);
        //【可选配置】用于控制 TTS 音频的语速，支持的配置范围参考火山官网 语音技术/语音合成/离在线语音合成SDK/参数说明 文档
        mTtsSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_SPEED_INT, 10);
        //【可选配置】用于控制 TTS 音频的音量，支持的配置范围参考火山官网 语音技术/语音合成/离在线语音合成SDK/参数说明 文档
        mTtsSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_VOLUME_INT, 10);
        mTtsSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_PITCH_INT, 10);
        mTtsSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_SILENCE_DURATION_INT, 0);
        mTtsSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_TTS_ENABLE_PLAYER_BOOL,false);
        mTtsSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_DATA_CALLBACK_MODE_INT,2);
        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_VOICE_ONLINE_STRING,SensitiveDefines.DEFUALT_VOICE_USER);
        mTtsSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_VOICE_TYPE_ONLINE_STRING,
                SensitiveDefines.geTtsVoice(MyApplication.getInstance()));
        //【可选配置】是否启用在线合成的情感预测功能
        mTtsSpeechEngine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_TTS_WITH_INTENT_BOOL, true);
        //【可选配置】指定在线合成的情感，例如 happy, sad 等
        //【可选配置】需要返回详细的播放进度时应配置为 1, 否则配置为 0 或不配置
        mTtsSpeechEngine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_WITH_FRONTEND_INT, 1);

    }

    public static interface AsrCallBackListener {
        public void speechAsrResult(String data, boolean isFinish);

        public void speechStart(String id);

        public void speechStop();

        public void callBackTtsData(String name);
    }
}
