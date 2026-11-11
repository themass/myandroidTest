package com.openapi.feature.content.sound;

import com.openapi.speech.subtitle.api.TranslationOverlaySession;
import com.openapi.speech.subtitle.api.VideoTranslationRegistry;
import com.openapi.speech.subtitle.api.VideoTranslationRequest;
import com.openapi.speech.subtitle.realtime.VideoRealtimeSubtitleSession;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.openapi.core.favorite.MyFavoriteView;
import com.openapi.core.model.SoundItemsVo;

/**
 * 音乐播放页绑定火山流式字幕（频道的声音页和声音详情页用；视频页用 RealtimeSubtitleBindings）。
 * 调用方提供当前曲目地址和播放状态，这里只负责把字幕会话挂到收藏条上。
 */
public final class MusicSubtitleBindings {

    private MusicSubtitleBindings() {
    }

    /** 当前曲目来源；返回 null 表示没有可播放的曲目。 */
    public interface TrackProvider {
        @Nullable
        SoundItemsVo currentTrack();
    }

    /**
     * 声音页的翻译和流式字幕一起绑定到收藏条。频道声音页和声音详情页共用。
     */
    public static void bindTrackSession(MyFavoriteView favoriteView,
                                        Context context,
                                        ViewGroup overlayParent,
                                        TrackProvider tracks,
                                        MusicPlaybackProbe playbackProbe) {
        favoriteView.setTranslationSession(new TranslationOverlaySession() {
            @Override
            public ViewGroup getOverlayParent() {
                return overlayParent;
            }

            @Override
            public void fillRequest(VideoTranslationRequest.Builder builder) {
                SoundItemsVo t = tracks.currentTrack();
                if (t == null || t.file == null || t.file.isEmpty()) {
                    throw new IllegalStateException("no audio url");
                }
                builder.setVideoUrl(t.file)
                        .putExtra(VideoTranslationRequest.EXTRA_MEDIA_KIND,
                                VideoTranslationRequest.MEDIA_KIND_AUDIO);
            }

            @Override
            public VideoTranslationRegistry registry() {
                return VideoTranslationRegistry.getInstance();
            }
        });
        bind(favoriteView, context, overlayParent,
                () -> {
                    SoundItemsVo t = tracks.currentTrack();
                    return t != null && t.file != null ? t.file : "";
                },
                playbackProbe);
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
