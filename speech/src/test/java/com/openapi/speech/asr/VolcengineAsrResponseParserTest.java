package com.openapi.speech.asr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 识别响应解析的 JVM 单测（Robolectric 只为真 org.json）：
 * v2/v3 两种响应形态、业务错误码、空响应——这条链路上没有任何类型保护，全靠这里钉住。
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class VolcengineAsrResponseParserTest {

    @Test
    public void v3的result对象取text() {
        VolcengineAsrResponseParser.ParseResult parsed =
                VolcengineAsrResponseParser.parse("{\"result\":{\"text\":\" 今天天气不错 \"}}");
        assertTrue(!parsed.error);
        assertEquals("今天天气不错", parsed.text);
    }

    @Test
    public void v3没有text时按utterances拼接() {
        VolcengineAsrResponseParser.ParseResult parsed = VolcengineAsrResponseParser.parse(
                "{\"result\":{\"utterances\":[{\"text\":\"第一句\"},{\"text\":\"第二句\"}]}}");
        assertEquals("第一句\n第二句", parsed.text);
    }

    @Test
    public void v2的result数组拼接多段() {
        VolcengineAsrResponseParser.ParseResult parsed =
                VolcengineAsrResponseParser.parse("{\"result\":[{\"text\":\"你好\"},\"世界\"]}");
        assertEquals("你好\n世界", parsed.text);
    }

    @Test
    public void 业务错误码报出服务端文案而不是当文本() {
        VolcengineAsrResponseParser.ParseResult parsed = VolcengineAsrResponseParser.parse(
                "{\"code\":45000151,\"message\":\"audio format invalid\"}");
        assertTrue(parsed.error);
        assertEquals("audio format invalid", parsed.errorMessage);
        assertNull(parsed.text);
    }

    @Test
    public void 静音码不算错误() {
        // 1013 是「音频静音」，长静音片段的正常回包，不能把会话打断
        VolcengineAsrResponseParser.ParseResult parsed = VolcengineAsrResponseParser.parse(
                "{\"code\":1013,\"message\":\"silence\"}");
        assertTrue(!parsed.error);
    }

    @Test
    public void 没有可展示文本时空着而不是报错() {
        VolcengineAsrResponseParser.ParseResult parsed = VolcengineAsrResponseParser.parse("{\"result\":{}}");
        assertTrue(!parsed.error);
        assertNull(parsed.text);
    }

    @Test
    public void 空串和坏JSON都不崩() {
        assertNull(VolcengineAsrResponseParser.parse("").text);
        assertNotNull(VolcengineAsrResponseParser.parse("not json"));
    }
}
