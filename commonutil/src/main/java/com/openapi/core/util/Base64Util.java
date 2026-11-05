package com.openapi.core.util;

import java.util.Base64;

public class Base64Util {

    /**
     * 解码Base64编码的字符串为原始字符串。
     *
     * @param content 待解码的Base64编码字符串
     * @return 解码后的原始字符串
     */
    public static byte[] decodeBase64(String content) {
        // 使用Base64解码器进行解码
        return Base64.getDecoder().decode(content);
    }

    public static String encodeBase64(byte[] bytes) {
        // 使用Base64解码器进行解码
        return Base64.getEncoder().encodeToString(bytes);
    }
    // 其他工具方法...
}