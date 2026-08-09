package com.openapi.ks.myapp.video.vlc;

import android.content.Context;

import org.videolan.libvlc.LibVLC;

import java.util.ArrayList;

/**
 * 进程内单例 LibVLC，避免每个 GSY 实例各建一套 native 运行时。
 */
final class LibVlcHolder {

    private static volatile LibVLC sInstance;

    private LibVlcHolder() {
    }

    static LibVLC get(Context context) {
        if (sInstance == null) {
            synchronized (LibVlcHolder.class) {
                if (sInstance == null) {
                    Context app = context.getApplicationContext();
                    ArrayList<String> opts = new ArrayList<>();
                    // 每个选项必须是完整的 --key=value 单字符串；拆成两个元素会导致解析错误
                    opts.add("--avcodec-skiploopfilter=0");
                    opts.add("--network-caching=2000");
                    sInstance = new LibVLC(app, opts);
                }
            }
        }
        return sInstance;
    }
}
