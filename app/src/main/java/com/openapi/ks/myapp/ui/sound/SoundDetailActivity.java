package com.openapi.ks.myapp.ui.sound;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.openapi.core.event.FavoriteVo;
import com.openapi.core.view.translation.TranslationOverlaySession;
import com.openapi.core.view.translation.VideoTranslationRegistry;
import com.openapi.core.view.translation.VideoTranslationRequest;
import com.openapi.core.view.view.MyFavoriteView;
import com.openapi.core.view.view.MarqueeTextView;
import com.openapi.core.util.LogUtil;
import com.openapi.core.util.MediaUtil;
import com.openapi.core.util.PreferenceUtils;
import com.openapi.core.util.StaticDataUtil;
import com.openapi.core.util.ToastUtil;
import com.openapi.core.net.NetUtils;
import com.openapi.ks.chat.R;
import com.openapi.ks.chat.databinding.LayoutSoundDetailBinding;
import com.openapi.ks.chat.databinding.LayoutSoundStatusBinding;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.bean.vo.SoundItemsVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.service.PlayService;
import com.openapi.ks.myapp.translation.realtime.RealtimeSubtitleBindings;
import com.openapi.ks.myapp.translation.realtime.VideoRealtimeSubtitleSession;

import static com.openapi.ks.myapp.service.PlayService.CURRENT;
import static com.openapi.ks.myapp.service.PlayService.CURRENTTIME;
import static com.openapi.ks.myapp.service.PlayService.DURATION;
import static com.openapi.ks.myapp.service.PlayService.MUSIC_CURRENT;
import static com.openapi.ks.myapp.service.PlayService.MUSIC_DURATION;
import static com.openapi.ks.myapp.service.PlayService.MUSIC_PREPARED;
import static com.openapi.ks.myapp.service.PlayService.MUSIC_PREPAREING;
import static com.openapi.ks.myapp.service.PlayService.UPDATE_ACTION;

/**
 * 音乐「详情」全屏页：与列表共用 {@link PlayService}，支持倍速与翻译字幕（与视频页同一套组件）。
 */
public class SoundDetailActivity extends AppCompatActivity
        implements SeekBar.OnSeekBarChangeListener, MyFavoriteView.OnFavoriteItemClick {

    android.view.ViewGroup detailRoot;
    MarqueeTextView trackTitle;
    ImageView artView;
    @Nullable
    SeekBar progressView;
    @Nullable
    android.widget.ImageView ivPre;
    @Nullable
    android.widget.ImageView ivPlay;
    @Nullable
    android.widget.ImageView ivNext;
    @Nullable
    TextView tvTime;
    @Nullable
    TextView tvDuration;
    @Nullable
    ProgressBar pbLoading;
    MyFavoriteView myFavoriteView;

    private RecommendVo vo;
    private PlayService mService;
    private boolean mBound;
    private int current = -1;
    private boolean ignorNet;
    private PlayerReceiver homeReceiver;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((PlayService.LocalBinder) service).getService();
            mBound = true;
            syncUiFromService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vo = StaticDataUtil.get(Constants.SOUND_DETAIL_VO, RecommendVo.class);
        if (vo == null) {
            finish();
            return;
        }
        StaticDataUtil.del(Constants.SOUND_DETAIL_VO);
        LayoutSoundDetailBinding binding = LayoutSoundDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // progress, iv_pre, ... live in the <include> layout_sound_status (id music_status)
        LayoutSoundStatusBinding status = binding.musicStatus;
        detailRoot = binding.musicDetailRoot;
        trackTitle = binding.musicDetailTrackTitle;
        artView = binding.musicDetailArt;
        progressView = status.progress;
        ivPre = status.ivPre;
        ivPlay = status.ivPlay;
        ivNext = status.ivNext;
        tvTime = status.time;
        tvDuration = status.duration;
        pbLoading = status.pbLoading;
        myFavoriteView = binding.myFavoriteview;
        binding.musicDetailBack.setOnClickListener(v -> onBackClicked());
        if (PreferenceUtils.getPrefBoolean(this, Constants.MUSIC_UI_AUXIO_STYLE, false)) {
            detailRoot.setBackgroundColor(ContextCompat.getColor(this, R.color.music_auxio_background));
        }

        Intent intent = new Intent(this, PlayService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        ivPre.setOnClickListener(this::onTransportClick);
        ivPlay.setOnClickListener(this::onTransportClick);
        ivNext.setOnClickListener(this::onTransportClick);
        progressView.setOnSeekBarChangeListener(this);

        myFavoriteView.initFavoriteBackGroud(vo.param);
        myFavoriteView.setListener(this);
        myFavoriteView.showVideoLocal();
        myFavoriteView.setTranslationSession(new TranslationOverlaySession() {
            @Override
            public android.view.ViewGroup getOverlayParent() {
                return detailRoot;
            }

            @Override
            public void fillRequest(VideoTranslationRequest.Builder builder) {
                SoundItemsVo t = mService != null ? mService.getCurrentTrack() : null;
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

        VideoRealtimeSubtitleSession realtimeSession = RealtimeSubtitleBindings.forMusic(
                this,
                detailRoot,
                () -> {
                    SoundItemsVo t = mService != null ? mService.getCurrentTrack() : null;
                    return t != null && t.file != null ? t.file : "";
                },
                new RealtimeSubtitleBindings.MusicPlaybackProbe() {
                    @Override
                    public boolean isPlaying() {
                        return mService != null && mService.isPlaying();
                    }

                    @Override
                    public long positionMs() {
                        return mService != null ? mService.getPosition() : 0L;
                    }
                });
        RealtimeSubtitleBindings.attachMusic(myFavoriteView, realtimeSession,
                new RealtimeSubtitleBindings.MusicPlaybackProbe() {
                    @Override
                    public boolean isPlaying() {
                        return mService != null && mService.isPlaying();
                    }

                    @Override
                    public long positionMs() {
                        return mService != null ? mService.getPosition() : 0L;
                    }
                });

        homeReceiver = new PlayerReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UPDATE_ACTION);
        filter.addAction(MUSIC_CURRENT);
        filter.addAction(MUSIC_DURATION);
        filter.addAction(MUSIC_PREPAREING);
        filter.addAction(MUSIC_PREPARED);
        registerReceiver(homeReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (trackTitle != null) {
            trackTitle.setSelected(true);
        }
    }

    private void syncUiFromService() {
        if (mService == null) {
            return;
        }
        SoundItemsVo t = mService.getCurrentTrack();
        if (t != null) {
            trackTitle.setText(t.name);
        }
        if (mService.isPlaying()) {
            ivPlay.setImageResource(R.drawable.ic_music_pause_filled);
        } else {
            ivPlay.setImageResource(R.drawable.ic_music_play_filled);
        }
    }

    private void onTransportClick(View v) {
        if (mService == null) {
            return;
        }
        int id = v.getId();
        if (id == R.id.iv_play) {
            if (mService.isPlaying()) {
                mService.pause();
                ivPlay.setImageResource(R.drawable.ic_music_play_filled);
            } else {
                if (current == -1 && mService.getPlaylist() != null && !mService.getPlaylist().isEmpty()) {
                    mService.playPosi(0);
                } else {
                    mService.resume();
                }
                ivPlay.setImageResource(R.drawable.ic_music_pause_filled);
            }
        } else if (id == R.id.iv_next) {
            nextTrack();
        } else if (id == R.id.iv_pre) {
            prevTrack();
        }
    }

    private void nextTrack() {
        if (mService == null || mService.getPlaylist() == null) {
            return;
        }
        int n = mService.getPlaylist().size();
        int c = mService.getCurrentSongIndex();
        int next = c + 1;
        if (next >= n) {
            next = 0;
        }
        mService.playPosi(next);
        ivPlay.setImageResource(R.drawable.ic_music_pause_filled);
    }

    private void prevTrack() {
        if (mService == null || mService.getPlaylist() == null) {
            return;
        }
        int n = mService.getPlaylist().size();
        int c = mService.getCurrentSongIndex();
        int prev = c - 1;
        if (prev < 0) {
            prev = n - 1;
        }
        mService.playPosi(prev);
        ivPlay.setImageResource(R.drawable.ic_music_pause_filled);
    }

    void onBackClicked() {
        finish();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (mService != null) {
            mService.play(seekBar.getProgress());
            ivPlay.setImageResource(R.drawable.ic_music_pause_filled);
        }
    }

    @Override
    public FavoriteVo getFavoriteDataUrl() {
        return vo.tofavorite(Constants.FavoriteType.SOUND);
    }

    @Override
    public String getBrowserDataUrl() {
        if (mService == null) {
            return "";
        }
        SoundItemsVo t = mService.getCurrentTrack();
        return t != null && t.file != null ? t.file : "";
    }

    @Override
    public boolean setSpeed(float speed) {
        if (mService == null) {
            return false;
        }
        mService.setPlaybackSpeed(speed);
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
                    pbLoading.setVisibility(View.VISIBLE);
                    tvTime.setVisibility(View.GONE);
                    return;
                }
                pbLoading.setVisibility(View.GONE);
                tvTime.setVisibility(View.VISIBLE);
                if (action.equals(MUSIC_CURRENT)) {
                    int ct = intent.getIntExtra(CURRENTTIME, -1);
                    tvTime.setText(MediaUtil.formatTime(ct));
                    progressView.setProgress(ct);
                } else if (action.equals(MUSIC_DURATION)) {
                    int dur = intent.getIntExtra(DURATION, -1);
                    progressView.setMax(dur);
                    tvDuration.setText(MediaUtil.formatTime(dur));
                } else if (action.equals(UPDATE_ACTION)) {
                    current = intent.getIntExtra(CURRENT, -1);
                    if (mService != null && mService.getPlaylist() != null
                            && current >= 0 && current < mService.getPlaylist().size()) {
                        trackTitle.setText(mService.getPlaylist().get(current).name);
                    }
                } else if (action.equals(MUSIC_PREPARED)) {
                    if (!checkCanPlay() && mService != null && mService.isPlaying()) {
                        mService.pause();
                        ivPlay.setImageResource(R.drawable.ic_music_play_filled);
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
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        detailRoot = null;
        trackTitle = null;
        artView = null;
        progressView = null;
        ivPre = null;
        ivPlay = null;
        ivNext = null;
        tvTime = null;
        tvDuration = null;
        pbLoading = null;
        myFavoriteView = null;
        super.onDestroy();
    }
}
