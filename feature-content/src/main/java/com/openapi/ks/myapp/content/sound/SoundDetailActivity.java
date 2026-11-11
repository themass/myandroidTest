package com.openapi.ks.myapp.content.sound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.content.ContextCompat;

import com.openapi.core.db.FavoriteVo;
import com.openapi.ks.myapp.translation.api.TranslationOverlaySession;
import com.openapi.ks.myapp.translation.api.VideoTranslationRegistry;
import com.openapi.ks.myapp.translation.api.VideoTranslationRequest;
import com.openapi.core.favorite.MyFavoriteView;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.android.MediaUtil;
import com.openapi.common.util.android.PreferenceUtils;
import com.openapi.common.util.android.StaticDataUtil;
import com.openapi.common.net.NetUtils;
import com.openapi.feature.content.R;
import com.openapi.core.model.RecommendVo;
import com.openapi.core.model.SoundItemsVo;
import com.openapi.core.config.Constants;
import com.openapi.ks.myapp.content.sound.MusicSubtitleBindings;
import com.openapi.ks.myapp.content.sound.compose.SoundDetailActions;
import com.openapi.ks.myapp.content.sound.compose.SoundDetailScreenKt;
import com.openapi.ks.myapp.content.sound.compose.SoundDetailUiState;

import static com.openapi.ks.myapp.content.sound.PlayService.CURRENT;
import static com.openapi.ks.myapp.content.sound.PlayService.CURRENTTIME;
import static com.openapi.ks.myapp.content.sound.PlayService.DURATION;
import static com.openapi.ks.myapp.content.sound.PlayService.MUSIC_CURRENT;
import static com.openapi.ks.myapp.content.sound.PlayService.MUSIC_DURATION;
import static com.openapi.ks.myapp.content.sound.PlayService.MUSIC_PREPARED;
import static com.openapi.ks.myapp.content.sound.PlayService.MUSIC_PREPAREING;
import static com.openapi.ks.myapp.content.sound.PlayService.UPDATE_ACTION;

/**
 * 音乐「详情」全屏页：与列表共用 {@link PlayService}，支持倍速与翻译字幕（与视频页同一套组件）。
 * UI 为 Compose（{@code ui/sound/compose}）；收藏/倍速/译 工具条仍为 {@link MyFavoriteView}，
 * 因其翻译浮层需要挂载到 {@link #detailRoot}。
 */
public class SoundDetailActivity extends AppCompatActivity implements SoundDetailActions, MyFavoriteView.OnFavoriteItemClick {

    /** 根容器：翻译浮层挂载点；内部为 ComposeView。 */
    FrameLayout detailRoot;
    private final SoundDetailUiState uiState = new SoundDetailUiState();
    MyFavoriteView myFavoriteView;

    private RecommendVo vo;
    private int current = -1;
    private boolean ignorNet;
    private PlayerReceiver homeReceiver;

    private final MusicServiceConnection playback = new MusicServiceConnection(service -> syncUiFromService(), true);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vo = StaticDataUtil.get(Constants.SOUND_DETAIL_VO, RecommendVo.class);
        if (vo == null) {
            finish();
            return;
        }
        StaticDataUtil.del(Constants.SOUND_DETAIL_VO);

        detailRoot = new FrameLayout(this);
        ComposeView composeView = new ComposeView(this);
        detailRoot.addView(composeView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        detailRoot.setBackgroundResource(R.drawable.bg_music_list_page);
        if (PreferenceUtils.getPrefBoolean(this, Constants.MUSIC_UI_AUXIO_STYLE, false)) {
            detailRoot.setBackgroundColor(ContextCompat.getColor(this, R.color.music_auxio_background));
        }

        myFavoriteView = new MyFavoriteView(this);
        myFavoriteView.initFavoriteBackGroud(vo.param);
        myFavoriteView.setListener(this);
        myFavoriteView.showVideoLocal();
        myFavoriteView.setTranslationSession(new TranslationOverlaySession() {
            @Override
            public ViewGroup getOverlayParent() {
                return detailRoot;
            }

            @Override
            public void fillRequest(VideoTranslationRequest.Builder builder) {
                SoundItemsVo t = playback.service() != null ? playback.service().getCurrentTrack() : null;
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

        MusicSubtitleBindings.MusicPlaybackProbe probe = new MusicSubtitleBindings.MusicPlaybackProbe() {
            @Override
            public boolean isPlaying() {
                return playback.service() != null && playback.service().isPlaying();
            }

            @Override
            public long positionMs() {
                return playback.service() != null ? playback.service().getPosition() : 0L;
            }
        };
        MusicSubtitleBindings.bind(myFavoriteView, this, detailRoot,
                () -> {
                    SoundItemsVo t = playback.service() != null ? playback.service().getCurrentTrack() : null;
                    return t != null && t.file != null ? t.file : "";
                },
                probe);

        SoundDetailScreenKt.setSoundDetailContent(composeView, uiState, myFavoriteView, this);
        setContentView(detailRoot);

        playback.bind(this);

        homeReceiver = new PlayerReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UPDATE_ACTION);
        filter.addAction(MUSIC_CURRENT);
        filter.addAction(MUSIC_DURATION);
        filter.addAction(MUSIC_PREPAREING);
        filter.addAction(MUSIC_PREPARED);
        registerReceiver(homeReceiver, filter);
    }

    private void syncUiFromService() {
        if (playback.service() == null) {
            return;
        }
        SoundItemsVo t = playback.service().getCurrentTrack();
        if (t != null) {
            uiState.setTrackTitle(t.name);
        }
        uiState.setPlaying(playback.service().isPlaying());
    }

    @Override
    public void onPlayPauseClicked() {
        if (playback.service() == null) {
            return;
        }
        if (playback.service().isPlaying()) {
            playback.service().pause();
            uiState.setPlaying(false);
        } else {
            if (current == -1 && playback.service().getPlaylist() != null && !playback.service().getPlaylist().isEmpty()) {
                playback.service().playPosi(0);
            } else {
                playback.service().resume();
            }
            uiState.setPlaying(true);
        }
    }

    @Override
    public void onNextClicked() {
        nextTrack();
    }

    @Override
    public void onPreviousClicked() {
        prevTrack();
    }

    private void nextTrack() {
        if (playback.service() == null || playback.service().getPlaylist() == null) {
            return;
        }
        int n = playback.service().getPlaylist().size();
        int c = playback.service().getCurrentSongIndex();
        int next = c + 1;
        if (next >= n) {
            next = 0;
        }
        playback.service().playPosi(next);
        uiState.setPlaying(true);
    }

    private void prevTrack() {
        if (playback.service() == null || playback.service().getPlaylist() == null) {
            return;
        }
        int n = playback.service().getPlaylist().size();
        int c = playback.service().getCurrentSongIndex();
        int prev = c - 1;
        if (prev < 0) {
            prev = n - 1;
        }
        playback.service().playPosi(prev);
        uiState.setPlaying(true);
    }

    @Override
    public void onBackClicked() {
        finish();
    }

    @Override
    public void onSeekFinished(int positionMs) {
        if (playback.service() != null) {
            playback.service().play(positionMs);
            uiState.setPlaying(true);
        }
    }

    @Override
    public FavoriteVo getFavoriteDataUrl() {
        return vo.tofavorite(Constants.FavoriteType.SOUND);
    }

    @Override
    public String getBrowserDataUrl() {
        if (playback.service() == null) {
            return "";
        }
        SoundItemsVo t = playback.service().getCurrentTrack();
        return t != null && t.file != null ? t.file : "";
    }

    @Override
    public boolean setSpeed(float speed) {
        if (playback.service() == null) {
            return false;
        }
        playback.service().setPlaybackSpeed(speed);
        return true;
    }

    private boolean checkCanPlay() {
        boolean soundSwitch = PreferenceUtils.getPrefBoolean(this, Constants.SOUND_SWITCH, false);
        return soundSwitch || NetUtils.isWifi(this) || ignorNet;
    }

    public class PlayerReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                if (action == null) {
                    return;
                }
                if (action.equals(MUSIC_PREPAREING)) {
                    uiState.setPreparing(true);
                    return;
                }
                uiState.setPreparing(false);
                if (action.equals(MUSIC_CURRENT)) {
                    int ct = intent.getIntExtra(CURRENTTIME, -1);
                    uiState.setTimeText(MediaUtil.formatTime(ct));
                    uiState.setProgress(Math.max(0, Math.min(ct, uiState.getProgressMax())));
                } else if (action.equals(MUSIC_DURATION)) {
                    int dur = intent.getIntExtra(DURATION, -1);
                    uiState.setProgressMax(Math.max(0, dur));
                    uiState.setDurationText(MediaUtil.formatTime(dur));
                } else if (action.equals(UPDATE_ACTION)) {
                    current = intent.getIntExtra(CURRENT, -1);
                    if (playback.service() != null && playback.service().getPlaylist() != null
                            && current >= 0 && current < playback.service().getPlaylist().size()) {
                        uiState.setTrackTitle(playback.service().getPlaylist().get(current).name);
                    }
                } else if (action.equals(MUSIC_PREPARED)) {
                    if (!checkCanPlay() && playback.service() != null && playback.service().isPlaying()) {
                        playback.service().pause();
                        uiState.setPlaying(false);
                    }
                }
            } catch (Exception e) {
                LogUtil.e(e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (homeReceiver != null) {
            unregisterReceiver(homeReceiver);
        }
        if (myFavoriteView != null) {
            myFavoriteView.releaseTranslationOverlay();
        }
        playback.unbind(this);
        detailRoot = null;
        myFavoriteView = null;
        super.onDestroy();
    }
}
