package com.openai.commonutil.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.openai.commonutil.AndroidFileUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * 现代磁盘缓存实现 - 替代SimpleDiskCache
 * 使用Android内置API和Glide缓存机制
 */
public class ModernDiskCache {
    
    private static final String CACHE_DIR = "modern_cache";
    private final File cacheDir;
    private final long maxSize;
    
    public ModernDiskCache(Context context, long maxSize) {
        if (context != null) {
            this.cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        } else {
            // 如果context为null，使用临时目录
            this.cacheDir = new File(System.getProperty("java.io.tmpdir"), CACHE_DIR);
        }
        this.maxSize = maxSize;
        
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }
    
    /**
     * 获取输入流
     */
    public InputStreamEntry getInputStream(String key) throws IOException {
        File file = getFile(key);
        if (!file.exists()) {
            return null;
        }
        
        Map<String, Serializable> metadata = readMetadata(key);
        return new InputStreamEntry(new FileInputStream(file), metadata);
    }
    
    /**
     * 获取位图
     */
    public BitmapEntry getBitmap(String key) throws IOException {
        File file = getFile(key);
        if (!file.exists()) {
            return null;
        }
        
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) {
            return null;
        }
        
        Map<String, Serializable> metadata = readMetadata(key);
        return new BitmapEntry(bitmap, metadata);
    }
    
    /**
     * 获取字符串
     */
    public StringEntry getString(String key) throws IOException {
        File file = getFile(key);
        if (!file.exists()) {
            return null;
        }
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = AndroidFileUtils.toByteArray(fis);
            String content = new String(bytes, "UTF-8");
            Map<String, Serializable> metadata = readMetadata(key);
            return new StringEntry(content, metadata);
        }
    }
    
    /**
     * 检查是否包含键
     */
    public boolean contains(String key) {
        return getFile(key).exists();
    }
    
    /**
     * 打开输出流
     */
    public OutputStream openStream(String key) throws IOException {
        return openStream(key, new HashMap<String, Serializable>());
    }
    
    /**
     * 打开输出流，带元数据
     */
    public OutputStream openStream(String key, Map<String, ? extends Serializable> metadata) throws IOException {
        File file = getFile(key);
        writeMetadata(key, metadata);
        return new FileOutputStream(file);
    }
    
    /**
     * 存储输入流
     */
    public void put(String key, InputStream is) throws IOException {
        put(key, is, new HashMap<String, Serializable>());
    }
    
    /**
     * 存储输入流，带元数据
     */
    public void put(String key, InputStream is, Map<String, Serializable> metadata) throws IOException {
        try (OutputStream os = openStream(key, metadata)) {
            AndroidFileUtils.copy(is, os);
        }
    }
    
    /**
     * 存储字符串
     */
    public void put(String key, String value) throws IOException {
        put(key, value, new HashMap<String, Serializable>());
    }
    
    /**
     * 存储字符串，带元数据
     */
    public void put(String key, String value, Map<String, ? extends Serializable> metadata) throws IOException {
        try (OutputStream os = openStream(key, metadata)) {
            os.write(value.getBytes("UTF-8"));
        }
    }
    
    /**
     * 获取文件
     */
    private File getFile(String key) {
        return new File(cacheDir, md5(key));
    }
    
    /**
     * 获取元数据文件
     */
    private File getMetadataFile(String key) {
        return new File(cacheDir, md5(key) + ".meta");
    }
    
    /**
     * 写入元数据
     */
    private void writeMetadata(String key, Map<String, ? extends Serializable> metadata) throws IOException {
        File metaFile = getMetadataFile(key);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(metaFile))) {
            oos.writeObject(metadata);
        }
    }
    
    /**
     * 读取元数据
     */
    private Map<String, Serializable> readMetadata(String key) throws IOException {
        File metaFile = getMetadataFile(key);
        if (!metaFile.exists()) {
            return new HashMap<>();
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(metaFile))) {
            @SuppressWarnings("unchecked")
            Map<String, Serializable> metadata = (Map<String, Serializable>) ois.readObject();
            return metadata;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * MD5哈希
     */
    private String md5(String s) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(s.getBytes("UTF-8"));
            byte[] digest = m.digest();
            BigInteger bigInt = new BigInteger(1, digest);
            return bigInt.toString(16);
        } catch (Exception e) {
            throw new AssertionError();
        }
    }
    
    /**
     * 清理缓存
     */
    public void clear() {
        if (cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }
    
    /**
     * 获取缓存大小
     */
    public long getCacheSize() {
        long size = 0;
        if (cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    size += file.length();
                }
            }
        }
        return size;
    }
    
    /**
     * 输入流条目
     */
    public static class InputStreamEntry {
        private final InputStream inputStream;
        private final Map<String, Serializable> metadata;
        
        public InputStreamEntry(InputStream inputStream, Map<String, Serializable> metadata) {
            this.inputStream = inputStream;
            this.metadata = metadata;
        }
        
        public InputStream getInputStream() {
            return inputStream;
        }
        
        public Map<String, Serializable> getMetadata() {
            return metadata;
        }
        
        public void close() {
            AndroidFileUtils.closeQuietly(inputStream);
        }
    }
    
    /**
     * 位图条目
     */
    public static class BitmapEntry {
        private final Bitmap bitmap;
        private final Map<String, Serializable> metadata;
        
        public BitmapEntry(Bitmap bitmap, Map<String, Serializable> metadata) {
            this.bitmap = bitmap;
            this.metadata = metadata;
        }
        
        public Bitmap getBitmap() {
            return bitmap;
        }
        
        public Map<String, Serializable> getMetadata() {
            return metadata;
        }
    }
    
    /**
     * 字符串条目
     */
    public static class StringEntry {
        private final String string;
        private final Map<String, Serializable> metadata;
        
        public StringEntry(String string, Map<String, Serializable> metadata) {
            this.string = string;
            this.metadata = metadata;
        }
        
        public String getString() {
            return string;
        }
        
        public Map<String, Serializable> getMetadata() {
            return metadata;
        }
    }
}
