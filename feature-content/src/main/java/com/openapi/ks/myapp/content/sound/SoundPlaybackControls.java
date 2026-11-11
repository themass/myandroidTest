package com.openapi.ks.myapp.content.sound;

/**
 * 声音列表页的播放控制（从 {@link SoundItemsMusicFragment} 拆出，行为与拆分前一致）。
 * <p>
 * 只依赖接口，不依赖 Android 视图和 {@link PlayService}，因此可以在 JVM 单元测试中运行。
 * 当前曲目下标由广播回调 {@link #setCurrent(int)} 维护。
 */
final class SoundPlaybackControls {

    /** 播放服务的最小接口，只包含控制逻辑用到的方法。 */
    interface Playback {
        boolean isPlaying();

        int getPosition();

        void resume();

        void pause();

        void playPosi(int index);

        void seekTo(int positionMs);

        int computeNextIndex(int current, int count);

        int computePreviousIndex(int current, int count);
    }

    /** 播放服务的来源。服务未绑定时返回 null。 */
    interface PlaybackSource {
        Playback get();
    }

    /** 播放键图标。 */
    interface PlayIconSink {
        /** @param playing true 显示暂停图标，false 显示播放图标 */
        void showPlaying(boolean playing);
    }

    /** 播放模式：循环切换与图标刷新，由调用方（Fragment）实现，需要 Context 和 PlayService。 */
    interface PlayModeSink {
        void cycle();

        void apply();
    }

    /** 列表和界面回调。 */
    interface Host {
        int itemCount();

        void selectItem(int index);

        void showEmptyList();
    }

    private final Host host;
    private final PlaybackSource source;
    private final PlayIconSink playIcon;
    private final PlayModeSink modeSink;
    private int current = -1;

    SoundPlaybackControls(Host host, PlaybackSource source, PlayIconSink playIcon, PlayModeSink modeSink) {
        this.host = host;
        this.source = source;
        this.playIcon = playIcon;
        this.modeSink = modeSink;
    }

    /** 当前曲目下标，未开始播放时为 -1。 */
    int current() {
        return current;
    }

    void setCurrent(int index) {
        current = index;
    }

    boolean isPlaying() {
        Playback p = source.get();
        return p != null && p.isPlaying();
    }

    /**
     * 继续播放。
     *
     * @return 继续后的播放位置；服务未绑定或已在播放时返回 -1
     */
    int resume() {
        Playback p = source.get();
        if (p == null) {
            return -1;
        }
        if (p.isPlaying()) {
            return -1;
        }
        p.resume();
        playIcon.showPlaying(true);
        return p.getPosition();
    }

    /**
     * 暂停播放。
     *
     * @return 暂停时的位置；未在播放时返回 -1
     */
    int pause() {
        Playback p = source.get();
        if (!p.isPlaying()) {
            return -1;
        }
        p.pause();
        playIcon.showPlaying(false);
        return p.getPosition();
    }

    /** 播放指定曲目。空列表时提示；越界时钳位。 */
    void play(int position) {
        int count = host.itemCount();
        if (count <= 0) {
            host.showEmptyList();
            return;
        }
        if (position < 0) {
            position = 0;
        }
        if (position == 0) {
            host.selectItem(0);
        }
        if (position >= count) {
            position = count - 1;
        }
        playIcon.showPlaying(true);
        source.get().playPosi(position);
    }

    void next() {
        Playback p = source.get();
        int count = host.itemCount();
        if (p == null || count <= 0) {
            return;
        }
        play(p.computeNextIndex(current, count));
    }

    void pre() {
        Playback p = source.get();
        int count = host.itemCount();
        if (p == null || count <= 0) {
            return;
        }
        play(p.computePreviousIndex(current, count));
    }

    /** 底栏播放键：播放中则暂停；未开始过则从第 0 首开始；否则继续。 */
    void togglePlayPause() {
        Playback p = source.get();
        if (p == null) {
            return;
        }
        if (p.isPlaying()) {
            pause();
        } else if (current == -1) {
            play(0);
        } else {
            resume();
        }
    }

    /** 进度条拖动松手后跳转到指定位置。 */
    void seekTo(int positionMs) {
        source.get().seekTo(positionMs);
        playIcon.showPlaying(true);
    }

    void cyclePlayMode() {
        if (source.get() == null || modeSink == null) {
            return;
        }
        modeSink.cycle();
    }

    void applyPlayModeIcon() {
        if (modeSink == null) {
            return;
        }
        modeSink.apply();
    }

    /** 按服务状态刷新播放键图标；服务未绑定时不改动。 */
    void syncPlayButtonFromService() {
        Playback p = source.get();
        if (p == null) {
            return;
        }
        playIcon.showPlaying(p.isPlaying());
    }
}
