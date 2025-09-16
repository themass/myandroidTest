package com.openai.commonutil.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

// 使用现代磁盘缓存实现
import com.openai.commonutil.AndroidFileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 简单磁盘缓存 - 使用现代实现
 * 已更新为使用Android内置API替代Commons IO和DiskLruCache
 */
public class SimpleDiskCache {

    private static final List<File> usedDirs = new ArrayList<>();
    private final ModernDiskCache modernCache;

    private SimpleDiskCache(File dir, int appVersion, long maxSize) throws IOException {
        // 使用Context创建现代缓存
        // 注意：这里需要传入Context，暂时使用null，实际使用时需要传入正确的Context
        this.modernCache = new ModernDiskCache(null, maxSize);
    }

    public static synchronized SimpleDiskCache open(File dir, int appVersion, long maxSize)
            throws IOException {
        if (usedDirs.contains(dir)) {
            throw new IllegalStateException("Cache dir " + dir.getAbsolutePath() + " was used before.");
        }

        usedDirs.add(dir);

        return new SimpleDiskCache(dir, appVersion, maxSize);
    }

    public InputStreamEntry getInputStream(String key) throws IOException {
        ModernDiskCache.InputStreamEntry entry = modernCache.getInputStream(key);
        if (entry == null) return null;
        return new InputStreamEntry(entry.getInputStream(), entry.getMetadata());
    }

    public BitmapEntry getBitmap(String key) throws IOException {
        ModernDiskCache.BitmapEntry entry = modernCache.getBitmap(key);
        if (entry == null) return null;
        return new BitmapEntry(entry.getBitmap(), entry.getMetadata());
    }

    public StringEntry getString(String key) throws IOException {
        ModernDiskCache.StringEntry entry = modernCache.getString(key);
        if (entry == null) return null;
        return new StringEntry(entry.getString(), entry.getMetadata());
    }

    public boolean contains(String key) throws IOException {
        return modernCache.contains(key);
    }

    public OutputStream openStream(String key) throws IOException {
        return modernCache.openStream(key);
    }

    public OutputStream openStream(String key, Map<String, ? extends Serializable> metadata)
            throws IOException {
        return modernCache.openStream(key, metadata);
    }

    public void put(String key, InputStream is) throws IOException {
        modernCache.put(key, is);
    }

    public void put(String key, InputStream is, Map<String, Serializable> annotations)
            throws IOException {
        modernCache.put(key, is, annotations);
    }

    public void put(String key, String value) throws IOException {
        modernCache.put(key, value);
    }

    public void put(String key, String value, Map<String, ? extends Serializable> annotations)
            throws IOException {
        modernCache.put(key, value, annotations);
    }

    // 使用ModernDiskCache的条目类
    public static class InputStreamEntry extends ModernDiskCache.InputStreamEntry {
        public InputStreamEntry(InputStream inputStream, Map<String, Serializable> metadata) {
            super(inputStream, metadata);
        }
    }

    public static class BitmapEntry extends ModernDiskCache.BitmapEntry {
        public BitmapEntry(Bitmap bitmap, Map<String, Serializable> metadata) {
            super(bitmap, metadata);
        }
    }

    public static class StringEntry extends ModernDiskCache.StringEntry {
        public StringEntry(String string, Map<String, Serializable> metadata) {
            super(string, metadata);
        }
    }
}
