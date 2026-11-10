package com.openapi.ks.myapp.translation.realtime;

import android.content.Context;
import android.view.ViewGroup;

import com.openapi.core.view.view.MyFavoriteView;

/** 音乐播放页绑定火山流式字幕（频道的声音页用；视频页用 RealtimeSubtitleBindings）。 */
public final class MusicSubtitleBindings {

    private MusicSubtitleBindings() {
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
