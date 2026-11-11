package com.openapi.ks.myapp.content.sound;

import com.openapi.ks.myapp.translation.realtime.VideoRealtimeSubtitleSession;

import android.content.Context;
import android.view.ViewGroup;

import com.openapi.core.favorite.MyFavoriteView;

/**
 * 音乐播放页绑定火山流式字幕（频道的声音页和声音详情页用；视频页用 RealtimeSubtitleBindings）。
 * 调用方提供当前曲目地址和播放状态，这里只负责把字幕会话挂到收藏条上。
 */
public final class MusicSubtitleBindings {

    private MusicSubtitleBindings() {
    }

    /** 一步完成：创建字幕会话并挂到收藏条上。 */
    public static void bind(MyFavoriteView favoriteView,
                            Context context,
                            ViewGroup overlayParent,
                            TrackUrlProvider trackUrlProvider,
                            MusicPlaybackProbe playbackProbe) {
        VideoRealtimeSubtitleSession session = forMusic(context, overlayParent, trackUrlProvider, playbackProbe);
        attachMusic(favoriteView, session, playbackProbe);
    }

    public static VideoRealtimeSubtitleSession forMusic(
            Context context,
            ViewGroup overlayParent,
            TrackUrlProvider trackUrlProvider,
            MusicPlaybackProbe playbackProbe) {
        return new VideoRealtimeSubtitleSession(
                context,
                overlayParent,
                trackUrlProvider::currentTrackUrl,
                () -> null,
                new VideoRealtimeSubtitleSession.PlaybackProbe() {
                    @Override
                    public boolean isPlaying() {
                        return playbackProbe != null && playbackProbe.isPlaying();
                    }

                    @Override
                    public long positionMs() {
                        return playbackProbe != null ? playbackProbe.positionMs() : 0L;
                    }
                });
    }

    public static void attachMusic(MyFavoriteView favoriteView,
                                   VideoRealtimeSubtitleSession session,
                                   MusicPlaybackProbe playbackProbe) {
        favoriteView.setRealtimeSubtitleSession(session);
        favoriteView.setStreamingPositionProvider(
                () -> playbackProbe != null ? playbackProbe.positionMs() : 0L);
    }

    public interface TrackUrlProvider {
        String currentTrackUrl();
    }

    public interface MusicPlaybackProbe {
        boolean isPlaying();

        long positionMs();
    }
}
