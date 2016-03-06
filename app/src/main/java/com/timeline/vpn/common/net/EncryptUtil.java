package com.timeline.vpn.common.net;

import com.timeline.vpn.common.util.LayzLog;

import org.apache.commons.codec.binary.Base64;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class EncryptUtil {
    private static final String HmacSHA1 = "HmacSHA1";

    public static String HMACSHA1Encode(String data, String key) throws Exception {
        byte[] byteHMAC = null;
        try {
            Mac mac = Mac.getInstance(HmacSHA1);
            SecretKeySpec spec = new SecretKeySpec(key.getBytes("utf8"), HmacSHA1);
            mac.init(spec);
            byteHMAC = mac.doFinal(data.getBytes("utf8"));
        } catch (InvalidKeyException e) {
            throw new Exception(e);
        } catch (NoSuchAlgorithmException ignore) {
            throw new Exception(ignore);
        }
        return new String(Base64.encodeBase64(byteHMAC));
    }

    /**
     * 生成BaseString
     *
     * @param args
     * @return
     */
    public static String getBaseString(Map<String, String> args) throws Exception {
        if (args == null || args.size() == 0) {
            return null;
        }
        //按参数key升序排列
        Map<String, String> map = new TreeMap(args);
        //各参数之间用&连接，key与value之间用%3D连接
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!isFine(entry.getKey()) || !isFine(entry.getValue())) {
                continue;
            }
            sb.append(entry.getKey()).append("%3D").append(entry.getValue()).append("&");
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return new String(Base64.encodeBase64(sb.toString().getBytes("utf8")));
    }

    /**
     * 生成key
     *
     * @param key
     * @return
     */
    public static String getKey(String key) throws Exception {
        return new String(Base64.encodeBase64(key.getBytes("utf8")));
    }

    /**
     * 验证token
     *
     * @param key
     * @param from
     * @param data
     * @param token
     * @return
     * @throws Exception
     */
    public static boolean checkToken(String key, String from, Map<String, String> data, String token) {
        //验证参数
        if (!isAllFine(key, token, from) || data == null || data.size() == 0) {
            return false;
        }

        try {
            String generatedToken = HMACSHA1Encode(getBaseString(data), getKey(key + from));
            if (!isFine(generatedToken)) {
                return false;
            } else {
                return generatedToken.equals(token);
            }
        } catch (Exception e) {
            LayzLog.e("check ReplyAPI token for %s errer %s", key, e);
        }
        return false;
    }

    /**
     * 判断字符串是否正常
     *
     * @param str
     * @return
     */
    public static boolean isFine(final String str) {
        try {
            if (str == null || str.trim().length() == 0) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAllFine(String... strs) {
        try {
            for (String s : strs) {
                if (!isFine(s)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 加密.
     *
     * @param content
     * @param key
     * @return
     * @throws Exception
     */
    public static String aesEncrypt(String content, String key) throws Exception {
        byte[] raw = key.getBytes();
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS7Padding", "BC"); // "算法/模式/补码方式"
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            byte[] encrypted = cipher.doFinal(content.getBytes("utf-8"));
            return new String(Base64.encodeBase64(encrypted), "UTF-8");// 此处使用BASE64做转码功能，同时能起到2次加密的作用。
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";

    }

    /**
     * 解密.
     *
     * @param content
     * @param key
     * @return
     */
    public static String aesDecrypt(String content, String key) {
        try {
            byte[] raw = key.getBytes();
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS7Padding", "BC");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            byte[] encrypted = Base64.decodeBase64(content.getBytes("utf-8"));
            try {
                byte[] original = cipher.doFinal(encrypted);
                String originalString = new String(original, "utf-8");
                return originalString;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
