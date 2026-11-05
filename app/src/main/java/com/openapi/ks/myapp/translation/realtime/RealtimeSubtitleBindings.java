package com.openapi.ks.myapp.translation.realtime;

import android.content.Context;
import android.view.ViewGroup;

import com.openapi.core.view.view.MyFavoriteView;
import com.openapi.ks.myapp.ui.gsy.DetailGSYVideoPlayer;
import com.shuyu.gsyvideoplayer.video.base.GSYVideoView;

import java.util.Map;

/** 播放页绑定火山流式字幕 Session，避免各 Activity/Fragment 重复样板代码。 */
public final class RealtimeSubtitleBindings {

    private RealtimeSubtitleBindings() {
    }

    public static VideoRealtimeSubtitleSession forGsyPlayer(
            Context context,
            ViewGroup overlayParent,
            DetailGSYVideoPlayer player,
            VideoRealtimeSubtitleSession.MediaUrlProvider urlProvider,
            VideoRealtimeSubtitleSession.HeadersProvider headersProvider) {
        return new VideoRealtimeSubtitleSession(
                context,
                overlayParent,
                () -> resolvePlaybackUrl(player, urlProvider),
                () -> resolvePlaybackHeaders(player, headersProvider),
                new VideoRealtimeSubtitleSession.PlaybackProbe() {
                    @Override
                    public boolean isPlaying() {
                        return player != null
                                && player.getCurrentState() == GSYVideoView.CURRENT_STATE_PLAYING;
                    }

                    @Override
                    public long positionMs() {
                        return player != null ? player.getCurrentPositionWhenPlaying() : 0L;
                    }
                });
    }

    private static String resolvePlaybackUrl(DetailGSYVideoPlayer player,
                                             VideoRealtimeSubtitleSession.MediaUrlProvider fallback) {
        if (player != null) {
            String live = player.getPlaybackUrl();
            if (live != null && !live.isEmpty()) {
                return live;
            }
        }
        return fallback != null ? fallback.getUrl() : "";
    }

    private static Map<String, String> resolvePlaybackHeaders(DetailGSYVideoPlayer player,
                                                              VideoRealtimeSubtitleSession.HeadersProvider fallback) {
        if (player != null) {
            Map<String, String> live = player.getPlaybackHeaders();
            if (live != null && !live.isEmpty()) {
                return live;
            }
        }
        return fallback != null ? fallback.getHeaders() : null;
    }

    public static void attachVideo(MyFavoriteView favoriteView,
                                   VideoRealtimeSubtitleSession session,
                                   DetailGSYVideoPlayer player) {
        favoriteView.setRealtimeSubtitleSession(session);
        favoriteView.setStreamingPositionProvider(
                () -> player != null ? player.getCurrentPositionWhenPlaying() : 0L);
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
