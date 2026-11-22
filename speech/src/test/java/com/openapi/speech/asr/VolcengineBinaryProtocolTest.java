package com.openapi.speech.asr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 火山流式识别二进制帧的 JVM 单测：请求帧头字段、末包标志、gzip 往返、服务端帧解回。
 * 这条协议没有服务端可对照，写错就是连不上或永远不出字，只能靠往返测试钉住。
 */
public class VolcengineBinaryProtocolTest {

    @Test
    public void 全量请求帧带版本与JSON序列化标记() throws Exception {
        byte[] frame = VolcengineBinaryProtocol.buildFullClientRequest(
                "{\"app\":{\"appid\":\"x\"}}".getBytes("UTF-8"), false);
        assertEquals(0x11, frame[0] & 0xFF);                 // version 1 | header 1 word
        assertEquals(VolcengineBinaryProtocol.MSG_FULL_CLIENT << 4, frame[1] & 0xF0);
        assertEquals(VolcengineBinaryProtocol.SER_JSON << 4, frame[2] & 0xF0);
        assertEquals(VolcengineBinaryProtocol.COMP_NONE, frame[2] & 0x0F);
    }

    @Test
    public void 末包标志只打在最后一帧上() throws Exception {
        byte[] pcm = new byte[320];
        byte[] first = VolcengineBinaryProtocol.buildAudioRequest(pcm, 0, pcm.length, false, false);
        byte[] last = VolcengineBinaryProtocol.buildAudioRequest(pcm, 0, pcm.length, true, false);
        assertEquals(0, first[1] & VolcengineBinaryProtocol.FLAG_LAST_AUDIO);
        assertEquals(VolcengineBinaryProtocol.FLAG_LAST_AUDIO, last[1] & VolcengineBinaryProtocol.FLAG_LAST_AUDIO);
        assertEquals(VolcengineBinaryProtocol.MSG_AUDIO_ONLY << 4, last[1] & 0xF0);
    }

    @Test
    public void 音频帧的载荷长度字段等于真实字节数() throws Exception {
        byte[] pcm = new byte[640];
        byte[] frame = VolcengineBinaryProtocol.buildAudioRequest(pcm, 0, pcm.length, false, false);
        int payloadSize = ((frame[4] & 0xFF) << 24) | ((frame[5] & 0xFF) << 16)
                | ((frame[6] & 0xFF) << 8) | (frame[7] & 0xFF);
        assertEquals("4 字节头 + 4 字节长度 + 原样音频", frame.length, 8 + pcm.length);
        assertEquals(pcm.length, payloadSize);
    }

    @Test
    public void gzip压缩的音频帧能原样解回来() throws Exception {
        byte[] pcm = new byte[960];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (byte) (i % 127);
        }
        byte[] frame = VolcengineBinaryProtocol.buildAudioRequest(pcm, 0, pcm.length, false, true);
        assertEquals(VolcengineBinaryProtocol.COMP_GZIP, frame[2] & 0x0F);

        // 自己按同一套帧格式解：头部 8 字节 + gzip 载荷 → 解压回原 pcm
        int payloadSize = ((frame[4] & 0xFF) << 24) | ((frame[5] & 0xFF) << 16)
                | ((frame[6] & 0xFF) << 8) | (frame[7] & 0xFF);
        byte[] payload = new byte[payloadSize];
        System.arraycopy(frame, 8, payload, 0, payloadSize);
        assertTrue("gzip 魔数", (payload[0] & 0xFF) == 0x1F && (payload[1] & 0xFF) == 0x8B);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPInputStream in = new java.util.zip.GZIPInputStream(
                new java.io.ByteArrayInputStream(payload))) {
            byte[] buf = new byte[512];
            int n;
            while ((n = in.read(buf)) >= 0) {
                bos.write(buf, 0, n);
            }
        }
        org.junit.Assert.assertArrayEquals(pcm, bos.toByteArray());
    }

    @Test
    public void 服务端全量帧能解出JSON文本() throws Exception {
        String json = "{\"result\":{\"text\":\"你好\"}}";
        byte[] body = json.getBytes("UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{0x11, (byte) (VolcengineBinaryProtocol.MSG_SERVER_FULL << 4), 0x00, 0x00});
        out.write(new byte[]{
                (byte) (body.length >> 24), (byte) (body.length >> 16),
                (byte) (body.length >> 8), (byte) body.length});
        out.write(body);

        VolcengineBinaryProtocol.ServerMessage message =
                VolcengineBinaryProtocol.parseServerMessage(out.toByteArray());
        assertNotNull(message);
        assertFalse(message.error);
        assertEquals(json, message.text);
    }

    @Test
    public void 服务端错误帧带可读文案() throws Exception {
        String body = "quota exceeded";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{0x11, (byte) (VolcengineBinaryProtocol.MSG_SERVER_ERROR << 4), 0x00, 0x00});
        // 错误码 1004（访问超额），大端 4 字节
        out.write(new byte[]{0, 0, 0x03, (byte) 0xEC});
        out.write(new byte[]{0, 0, 0, (byte) body.length()});
        out.write(body.getBytes("UTF-8"));

        VolcengineBinaryProtocol.ServerMessage message =
                VolcengineBinaryProtocol.parseServerMessage(out.toByteArray());
        assertNotNull(message);
        assertTrue("错误帧必须走 onFailure 而不是当识别结果", message.error);
        assertTrue("错误文案要带服务端的超频/超额解释，实际 " + message.text,
                message.text.contains("超额") || message.text.contains("1004"));
    }

    @Test
    public void ACK帧当握手成功而不是空结果() throws Exception {
        VolcengineBinaryProtocol.ServerMessage message = VolcengineBinaryProtocol.parseServerMessage(
                new byte[]{0x11, (byte) (VolcengineBinaryProtocol.MSG_SERVER_ACK << 4), 0x00, 0x00});
        assertNotNull(message);
        assertFalse(message.error);
    }

    @Test
    public void 太短的帧返回null不崩() throws Exception {
        assertEquals(null, VolcengineBinaryProtocol.parseServerMessage(new byte[]{0x11}));
        assertEquals(null, VolcengineBinaryProtocol.parseServerMessage(null));
    }
}
