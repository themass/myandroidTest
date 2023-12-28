package com.openapi.ks.myapp.ui.sound;

import com.openapi.ks.myapp.ui.sound.media.JZMediaIjk;
import com.shuyu.gsyvideoplayer.player.IPlayerManager;
import com.shuyu.gsyvideoplayer.player.IjkPlayerManager;

import cn.jzvd.JZMediaInterface;

public class JzvdPlayerFactory {

    private static Class<? extends JZMediaInterface> sPlayerManager;

    public static void setPlayManager(Class<? extends JZMediaInterface> playManager) {
        sPlayerManager = playManager;
    }

    public static Class<? extends JZMediaInterface> getPlayManager() {
        if (sPlayerManager == null) {
            return JZMediaIjk.class;
        }
        return sPlayerManager;
    }

}