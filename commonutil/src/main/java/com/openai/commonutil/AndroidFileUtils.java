package com.openai.commonutil;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Android内置文件工具类 - 替代Commons IO
 * 使用Android内置API实现文件操作功能
 */
public class AndroidFileUtils {
    
    /**
     * 将输入流复制到输出流
     * 替代 IOUtils.copy()
     */
    public static long copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        long count = 0;
        int n;
        while ((n = input.read(buffer)) != -1) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }
    
    /**
     * 将输入流复制到输出流，指定缓冲区大小
     * 替代 IOUtils.copy()
     */
    public static long copy(InputStream input, OutputStream output, int bufferSize) throws IOException {
        byte[] buffer = new byte[bufferSize];
        long count = 0;
        int n;
        while ((n = input.read(buffer)) != -1) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }
    
    /**
     * 将输入流转换为字节数组
     * 替代 IOUtils.toByteArray()
     */
    public static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }
    
    /**
     * 将输入流转换为字符串
     * 替代 IOUtils.toString()
     */
    public static String toString(InputStream input) throws IOException {
        return toString(input, "UTF-8");
    }
    
    /**
     * 将输入流转换为字符串，指定编码
     * 替代 IOUtils.toString()
     */
    public static String toString(InputStream input, String encoding) throws IOException {
        byte[] bytes = toByteArray(input);
        return new String(bytes, encoding);
    }
    
    /**
     * 安全关闭流
     * 替代 IOUtils.closeQuietly()
     */
    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // 忽略关闭异常
            }
        }
    }
    
    /**
     * 安全关闭多个流
     * 替代 IOUtils.closeQuietly()
     */
    public static void closeQuietly(Closeable... closeables) {
        if (closeables != null) {
            for (Closeable closeable : closeables) {
                closeQuietly(closeable);
            }
        }
    }
}
