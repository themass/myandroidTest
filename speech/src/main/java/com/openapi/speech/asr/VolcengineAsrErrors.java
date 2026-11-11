package com.openapi.speech.asr;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** 火山 ASR 错误码 → 可读说明（含服务端 JSON message）。 */
final class VolcengineAsrErrors {

    private static final Map<Integer, String> KNOWN = new HashMap<>();

    static {
        KNOWN.put(1001, "请求参数无效");
        KNOWN.put(1002, "无访问权限（Token/AppId/Cluster 错误或已过期）");
        KNOWN.put(1003, "访问超频");
        KNOWN.put(1004, "访问超额");
        KNOWN.put(1005, "服务器繁忙");
        KNOWN.put(1012, "音频格式无效");
        KNOWN.put(1013, "音频静音");
        KNOWN.put(45000001, "请求参数无效");
        KNOWN.put(45000002, "空音频");
        KNOWN.put(45000081, "等包超时");
        KNOWN.put(45000151, "音频格式不正确");
        KNOWN.put(45000030, "请求参数无效（检查 Cluster、Token、audio.format=pcm）");
        KNOWN.put(55000031, "服务器繁忙");
    }

    private VolcengineAsrErrors() {
    }

    static String format(int frameCode, @Nullable String body) {
        if (body == null || body.isEmpty()) {
            return hint(frameCode, null);
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            try {
                JSONObject root = new JSONObject(trimmed);
                int code = root.optInt("code", frameCode);
                String msg = root.optString("message", "");
                if (msg.isEmpty()) {
                    msg = root.optString("msg", "");
                }
                if (msg.isEmpty()) {
                    msg = root.optString("status_text", "");
                }
                return hint(code, msg.isEmpty() ? null : msg);
            } catch (Exception ignored) {
            }
            VolcengineAsrResponseParser.ParseResult parsed = VolcengineAsrResponseParser.parse(trimmed);
            if (parsed.error && parsed.errorMessage != null) {
                return parsed.errorMessage;
            }
        }
        if (isMostlyPrintable(trimmed)) {
            return hint(frameCode, trimmed);
        }
        return hint(frameCode, null);
    }

    private static String hint(int code, @Nullable String serverMessage) {
        String known = KNOWN.get(code);
        if (serverMessage != null && !serverMessage.isEmpty()) {
            if (known != null && !serverMessage.contains(known)) {
                return "ASR " + code + ": " + serverMessage + "（" + known + "）";
            }
            return "ASR " + code + ": " + serverMessage;
        }
        if (known != null) {
            return "ASR " + code + ": " + known;
        }
        return "ASR " + code + ": 请检查设置中的 App Key、Token、Cluster 是否正确";
    }

    private static boolean isMostlyPrintable(String s) {
        int printable = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || (c >= 32 && c < 127) || c >= 0x4E00) {
                printable++;
            }
        }
        return s.length() > 0 && printable * 100 / s.length() >= 80;
    }
}
