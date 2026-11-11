package com.openapi.speech.asr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** 火山 openspeech WebSocket 二进制帧（v2/v3 通用）。 */
final class VolcengineBinaryProtocol {

    private static final int VERSION = 0x1;
    private static final int HEADER_SIZE_WORDS = 0x1;

    static final int MSG_FULL_CLIENT = 0x1;
    static final int MSG_AUDIO_ONLY = 0x2;
    static final int MSG_SERVER_FULL = 0x9;
    static final int MSG_SERVER_ACK = 0xB;
    static final int MSG_SERVER_ERROR = 0xF;

    static final int SER_NONE = 0x0;
    static final int SER_JSON = 0x1;
    static final int COMP_NONE = 0x0;
    static final int COMP_GZIP = 0x1;

    static final int FLAG_LAST_AUDIO = 0x2;

    private VolcengineBinaryProtocol() {
    }

    static byte[] buildFullClientRequest(byte[] jsonUtf8, boolean gzip) throws IOException {
        byte[] payload = gzip ? gzip(jsonUtf8) : jsonUtf8;
        int compression = gzip ? COMP_GZIP : COMP_NONE;
        return buildFrame(MSG_FULL_CLIENT, 0, SER_JSON, compression, null, payload);
    }

    static byte[] buildAudioRequest(byte[] pcm, int offset, int length, boolean lastPacket, boolean gzip)
            throws IOException {
        byte[] slice = new byte[length];
        System.arraycopy(pcm, offset, slice, 0, length);
        byte[] payload = gzip ? gzip(slice) : slice;
        int flags = lastPacket ? FLAG_LAST_AUDIO : 0;
        int compression = gzip ? COMP_GZIP : COMP_NONE;
        return buildFrame(MSG_AUDIO_ONLY, flags, SER_NONE, compression, null, payload);
    }

    static ServerMessage parseServerMessage(byte[] data) throws IOException {
        if (data == null || data.length < 4) {
            return null;
        }
        int messageType = (data[1] >> 4) & 0xF;
        int flags = data[1] & 0xF;
        int compression = data[2] & 0xF;
        int offset = 4;

        if (messageType == MSG_SERVER_ERROR) {
            if (data.length < offset + 8) {
                return ServerMessage.error("ASR protocol error");
            }
            int code = readUint32Be(data, offset);
            offset += 4;
            int msgLen = readUint32Be(data, offset);
            offset += 4;
            int available = Math.max(0, data.length - offset);
            int copyLen = Math.min(msgLen, available);
            byte[] payloadBytes = new byte[copyLen];
            if (copyLen > 0) {
                System.arraycopy(data, offset, payloadBytes, 0, copyLen);
            }
            payloadBytes = maybeDecompress(payloadBytes, compression);
            String body = new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8);
            return ServerMessage.error(VolcengineAsrErrors.format(code, body));
        }

        if (messageType == MSG_SERVER_ACK) {
            // ACK：无 JSON 或后续跟 gzip JSON，视为握手成功
            return ServerMessage.json("");
        }

        if (messageType != MSG_SERVER_FULL) {
            return null;
        }

        // v2/v3 full server response：flags 含 sequence 时 header 后 4 字节为序号
        int payloadStart = offset;
        if ((flags & 0x01) != 0 && data.length >= offset + 4) {
            payloadStart = offset + 4;
        }
        if (data.length < payloadStart + 4) {
            return null;
        }
        int payloadSize = readUint32Be(data, payloadStart);
        payloadStart += 4;
        if (payloadSize <= 0 || data.length < payloadStart + payloadSize) {
            // 部分响应 payload 直接跟在 header 后（无 size 字段时尝试剩余字节）
            if (payloadSize <= 0 && data.length > payloadStart) {
                payloadSize = data.length - payloadStart;
            } else {
                return null;
            }
        }
        byte[] payload = new byte[payloadSize];
        System.arraycopy(data, payloadStart, payload, 0, payloadSize);
        payload = maybeDecompress(payload, compression);
        return ServerMessage.json(new String(payload, java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 按 header 压缩位解压；部分错误帧未置位但仍为 gzip。 */
    private static byte[] maybeDecompress(byte[] payload, int compression) throws IOException {
        if (payload == null || payload.length == 0) {
            return payload;
        }
        if (compression == COMP_GZIP || looksLikeGzip(payload)) {
            return gunzip(payload);
        }
        return payload;
    }

    private static boolean looksLikeGzip(byte[] data) {
        return data.length >= 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B;
    }

    private static byte[] buildFrame(int messageType, int flags, int serialization, int compression,
                                     byte[] sequence, byte[] payload) {
        int extra = sequence != null ? sequence.length : 0;
        byte[] out = new byte[4 + extra + 4 + payload.length];
        out[0] = (byte) ((VERSION << 4) | HEADER_SIZE_WORDS);
        out[1] = (byte) ((messageType << 4) | (flags & 0xF));
        out[2] = (byte) ((serialization << 4) | (compression & 0xF));
        out[3] = 0;
        int offset = 4;
        if (sequence != null) {
            System.arraycopy(sequence, 0, out, offset, sequence.length);
            offset += sequence.length;
        }
        writeUint32Be(out, offset, payload.length);
        System.arraycopy(payload, 0, out, offset + 4, payload.length);
        return out;
    }

    private static int readUint32Be(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt() & 0xFFFFFFFF;
    }

    private static void writeUint32Be(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }

    private static byte[] gzip(byte[] input) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length);
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(input);
        }
        return bos.toByteArray();
    }

    private static byte[] gunzip(byte[] input) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(input);
        try (GZIPInputStream gzip = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = gzip.read(buf)) >= 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    static final class ServerMessage {
        final boolean error;
        final String text;

        private ServerMessage(boolean error, String text) {
            this.error = error;
            this.text = text;
        }

        static ServerMessage json(String json) {
            return new ServerMessage(false, json);
        }

        static ServerMessage error(String msg) {
            return new ServerMessage(true, msg);
        }
    }
}
