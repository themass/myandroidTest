package com.openapi.ks.myapp.content.sound;

/**
 * Created by openapi on 2017/12/24.
 */

public interface MusicStateListener {

    boolean isPlaying();

    /**
     * 列表行左侧是否应显示「正在播」频谱动画。
     * 默认仅 {@link #isPlaying()}；实现类可在缓冲/准备阶段返回 true，避免 UPDATE_ACTION 早于 start 导致永远不刷频谱。
     */
    default boolean showMusicListSpectrum() {
        return isPlaying();
    }
}
