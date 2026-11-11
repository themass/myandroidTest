package com.openapi.ks.myapp.translation.realtime;

import androidx.annotation.Nullable;

import com.openapi.common.util.LogUtil;

import org.json.JSONArray;
import org.json.JSONObject;

/** 从火山 ASR JSON 响应中提取展示文本或错误。 */
final class VolcengineAsrResponseParser {

    static final int CODE_OK = 1000;

    static final class ParseResult {
        final boolean error;
        @Nullable
        final String errorMessage;
        @Nullable
        final String text;

        private ParseResult(boolean error, @Nullable String errorMessage, @Nullable String text) {
            this.error = error;
            this.errorMessage = errorMessage;
            this.text = text;
        }

        static ParseResult error(String message) {
            return new ParseResult(true, message, null);
        }

        static ParseResult text(String text) {
            return new ParseResult(false, null, text);
        }

        static ParseResult empty() {
            return new ParseResult(false, null, null);
        }
    }

    private VolcengineAsrResponseParser() {
    }

    static ParseResult parse(String json) {
        if (json == null || json.isEmpty()) {
            return ParseResult.empty();
        }
        try {
            JSONObject root = new JSONObject(json);

            if (root.has("code")) {
                int code = root.optInt("code", -1);
                if (code != CODE_OK && code != 1013 && code != 20000000 && code != 20000001 && code != 20000002) {
                    String msg = root.optString("message", "");
                    if (msg.isEmpty()) {
                        msg = root.optString("msg", "");
                    }
                    if (msg.isEmpty()) {
                        msg = "ASR error code " + code;
                    }
                    return ParseResult.error(msg);
                }
            }

            String fromV3 = extractV3ResultObject(root);
            if (fromV3 != null && !fromV3.isEmpty()) {
                return ParseResult.text(fromV3);
            }

            String fromV2 = extractV2ResultArray(root);
            if (fromV2 != null && !fromV2.isEmpty()) {
                return ParseResult.text(fromV2);
            }

            String direct = root.optString("text", null);
            if (direct != null && !direct.isEmpty()) {
                return ParseResult.text(direct.trim());
            }
        } catch (Exception e) {
            LogUtil.e("VolcengineAsrResponseParser", e);
        }
        return ParseResult.empty();
    }

    /** 兼容旧调用。 */
    @Nullable
    static String extractDisplayText(String json) {
        ParseResult result = parse(json);
        return result.error ? null : result.text;
    }

    @Nullable
    private static String extractV2ResultArray(JSONObject root) {
        JSONArray resultArr = root.optJSONArray("result");
        if (resultArr == null || resultArr.length() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < resultArr.length(); i++) {
            Object item = resultArr.opt(i);
            if (item instanceof JSONObject) {
                appendLine(sb, ((JSONObject) item).optString("text", ""));
            } else if (item instanceof String) {
                appendLine(sb, (String) item);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    @Nullable
    private static String extractV3ResultObject(JSONObject root) {
        if (!root.has("result")) {
            return null;
        }
        Object resultObj = root.opt("result");
        if (!(resultObj instanceof JSONObject)) {
            return null;
        }
        JSONObject result = (JSONObject) resultObj;
        String text = result.optString("text", null);
        if (text != null && !text.isEmpty()) {
            return text.trim();
        }
        JSONArray utterances = result.optJSONArray("utterances");
        if (utterances == null || utterances.length() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < utterances.length(); i++) {
            JSONObject u = utterances.optJSONObject(i);
            if (u != null) {
                appendLine(sb, u.optString("text", ""));
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static void appendLine(StringBuilder sb, String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(line.trim());
    }
}
