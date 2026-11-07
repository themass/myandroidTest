package com.openapi.ks.myapp.translation.realtime;

import com.openapi.core.util.LogUtil;

import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * 火山 openspeech 双向流式 WebSocket 客户端（v3 bigmodel_async / v2 asr）。
 */
public final class VolcengineStreamingAsrClient {

    public interface Listener {
        void onReady();

        void onPartialText(String text);

        void onFailure(String message);

        void onClosed();
    }

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();

    private final VolcengineAsrConfig config;
    private WebSocket webSocket;
    private Listener listener;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    /**
     * 音频发送门闩。WebSocket 握手完成、初始化帧发出之前录到的音频先排在这里，
     * 初始化帧发出后按顺序冲刷。否则录音线程一开始就发音频，服务端收到的第一帧不是
     * 初始化请求，会直接拒绝，界面上表现为「按住说话后没有任何文字」。
     */
    private final Object sendLock = new Object();
    private final ArrayDeque<byte[]> pendingAudio = new ArrayDeque<>();
    private boolean sendOpen = false;
    /** 排队上限：约 10 秒 16kHz 16bit 单声道，超出从最旧的丢。 */
    private static final int MAX_PENDING_BYTES = 16000 * 2 * 10;
    private int pendingBytes = 0;

    public VolcengineStreamingAsrClient(VolcengineAsrConfig config) {
        this.config = config;
    }

    public void connect(Listener listener) {
        this.listener = listener;
        closed.set(false);
        ready.set(false);
        synchronized (sendLock) {
            sendOpen = false;
            pendingAudio.clear();
            pendingBytes = 0;
        }

        Request.Builder rb = new Request.Builder().url(config.websocketUrl());
        if (config.useV3) {
            rb.header("X-Api-App-Key", config.appKey);
            rb.header("X-Api-Access-Key", config.accessKey);
            rb.header("X-Api-Resource-Id", config.resourceId);
            rb.header("X-Api-Connect-Id", UUID.randomUUID().toString());
        } else {
            // v2 建连必须在 Header 中带 Bearer Token，否则鉴权失败
            rb.header("Authorization", "Bearer;" + config.accessKey);
        }

        webSocket = HTTP.newWebSocket(rb.build(), new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                LogUtil.i("VolcengineAsr", "WebSocket open HTTP " + response.code());
                try {
                    String initJson = buildInitJson();
                    LogUtil.i("VolcengineAsr init: " + initJson);
                    boolean gzip = true;
                    byte[] frame = VolcengineBinaryProtocol.buildFullClientRequest(
                            initJson.getBytes(), gzip);
                    webSocket.send(ByteString.of(frame));
                    // 初始化帧已经排在队列最前，之后的音频帧保证在它之后发出
                    synchronized (sendLock) {
                        sendOpen = true;
                        while (!pendingAudio.isEmpty()) {
                            byte[] queued = pendingAudio.poll();
                            webSocket.send(ByteString.of(VolcengineBinaryProtocol.buildAudioRequest(
                                    queued, 0, queued.length, false, true)));
                        }
                        pendingBytes = 0;
                    }
                } catch (Exception e) {
                    LogUtil.e(e);
                    notifyFailure("ASR init failed: " + e.getMessage());
                    closeQuietly();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                handleBinary(bytes.toByteArray());
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                LogUtil.w("VolcengineAsr text: " + text);
                handlePayloadText(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                LogUtil.e(t);
                String detail = t.getMessage() != null ? t.getMessage() : "WebSocket failed";
                if (response != null) {
                    detail = "HTTP " + response.code() + " " + response.message()
                            + (detail.isEmpty() ? "" : (" (" + detail + ")"));
                }
                notifyFailure("ASR connect failed: " + detail);
                closeQuietly();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                closed.set(true);
                if (listener != null) {
                    listener.onClosed();
                }
            }
        });
    }

    public void sendPcm(byte[] buffer, int offset, int length, boolean last) {
        WebSocket ws = webSocket;
        if (ws == null || closed.get()) {
            return;
        }
        try {
            synchronized (sendLock) {
                if (!sendOpen) {
                    // 握手未完成：先排队，onOpen 里冲刷。结束帧不排队：未连上时 finish 之后
                    // 连接马上关闭，没有可以补发结束帧的时机。
                    if (!last && length > 0) {
                        byte[] copy = Arrays.copyOfRange(buffer, offset, offset + length);
                        pendingAudio.add(copy);
                        pendingBytes += copy.length;
                        while (pendingBytes > MAX_PENDING_BYTES && !pendingAudio.isEmpty()) {
                            pendingBytes -= pendingAudio.poll().length;
                        }
                    }
                    return;
                }
            }
            byte[] frame = VolcengineBinaryProtocol.buildAudioRequest(
                    buffer, offset, length, last, true);
            ws.send(ByteString.of(frame));
        } catch (Exception e) {
            LogUtil.e(e);
            notifyFailure("send audio failed: " + e.getMessage());
        }
    }

    public void finish() {
        sendPcm(new byte[0], 0, 0, true);
        closeQuietly();
    }

    public void cancel() {
        closeQuietly();
    }

    private void handleBinary(byte[] data) {
        try {
            VolcengineBinaryProtocol.ServerMessage msg = VolcengineBinaryProtocol.parseServerMessage(data);
            if (msg == null) {
                markReadyOnce();
                return;
            }
            if (msg.error) {
                notifyFailure(msg.text);
                closeQuietly();
                return;
            }
            handlePayloadText(msg.text);
        } catch (Exception e) {
            LogUtil.e(e);
            notifyFailure("ASR response parse failed: " + e.getMessage());
            closeQuietly();
        }
    }

    private void handlePayloadText(String json) {
        if (json == null || json.isEmpty()) {
            markReadyOnce();
            return;
        }
        LogUtil.i("VolcengineAsr resp: " + json);
        VolcengineAsrResponseParser.ParseResult parsed = VolcengineAsrResponseParser.parse(json);
        if (parsed.error) {
            notifyFailure(parsed.errorMessage != null ? parsed.errorMessage : "ASR failed");
            closeQuietly();
            return;
        }
        markReadyOnce();
        if (parsed.text != null && !parsed.text.isEmpty() && listener != null) {
            listener.onPartialText(parsed.text);
        }
    }

    private String buildInitJson() throws Exception {
        JSONObject user = new JSONObject();
        user.put("uid", "android-" + UUID.randomUUID());

        JSONObject audio = new JSONObject();
        // format=容器(pcm/wav)，codec=编码(raw=pcm_s16le)
        audio.put("format", "pcm");
        audio.put("codec", "raw");
        audio.put("rate", 16000);
        audio.put("bits", 16);
        audio.put("channel", 1);

        if (config.useV3) {
            audio.put("language", "zh-CN");
            JSONObject request = new JSONObject();
            request.put("model_name", "bigmodel");
            request.put("enable_punc", true);
            request.put("show_utterances", true);
            request.put("result_type", "single");

            JSONObject root = new JSONObject();
            root.put("user", user);
            root.put("audio", audio);
            root.put("request", request);
            return root.toString();
        }

        JSONObject app = new JSONObject();
        app.put("appid", config.appKey);
        app.put("token", config.accessKey);
        app.put("cluster", config.cluster);

        JSONObject request = new JSONObject();
        request.put("reqid", UUID.randomUUID().toString());
        request.put("sequence", 1);
        request.put("nbest", 1);
        request.put("result_type", "single");
        request.put("show_utterances", true);
        // 与火山官方 Go Demo 一致（过长 workflow 可能触发 45000030）
        request.put("workflow", "audio_in,resample,partition,vad,fe,decode");

        JSONObject root = new JSONObject();
        root.put("app", app);
        root.put("user", user);
        root.put("audio", audio);
        root.put("request", request);
        return root.toString();
    }

    private void markReadyOnce() {
        if (ready.compareAndSet(false, true) && listener != null) {
            listener.onReady();
        }
    }

    private void notifyFailure(String message) {
        LogUtil.e("VolcengineAsr: " + message);
        if (listener != null) {
            listener.onFailure(message);
        }
    }

    private void closeQuietly() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            try {
                ws.close(1000, "done");
            } catch (Exception ignored) {
            }
        }
    }
}
