package com.timeline.vpn.common.util.cache;

import com.android.volley.toolbox.DiskBasedCache;

import java.io.File;

/**
 * @author jrzheng 2014-10-14.
 */
public class DiskBasedCacheEx extends DiskBasedCache {
    public static final int MAX_ENTRY_SIZE = 100 * 1024;

    public DiskBasedCacheEx(File rootDirectory, int maxCacheSizeInBytes) {
        super(rootDirectory, maxCacheSizeInBytes);
    }

    public DiskBasedCacheEx(File rootDirectory) {
        super(rootDirectory);
    }

    public File getFileForKey(String key) {
        File file = super.getFileForKey(key);
        if (file.exists()) {
            if (file.length() > MAX_ENTRY_SIZE) {
                //缓存文件大于100K，删除
                file.delete();
            }
        }
        return file;
    }
}
