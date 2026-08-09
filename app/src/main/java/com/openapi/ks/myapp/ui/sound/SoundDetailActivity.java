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

import com.openai.commonevent.FavoriteVo;
import com.openai.commonview.translation.TranslationOverlaySession;
import com.openai.commonview.translation.VideoTranslationRegistry;
import com.openai.commonview.translation.VideoTranslationRequest;
import com.openai.commonview.view.MyFavoriteView;
import com.openai.commonview.view.MarqueeTextView;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.MediaUtil;
import com.openai.commonutil.PreferenceUtils;
import com.openai.commonutil.StaticDataUtil;
import com.openai.commonutil.ToastUtil;
import com.openai.net.NetUtils;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.bean.vo.SoundItemsVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.service.PlayService;
import com.openapi.ks.myapp.translation.realtime.RealtimeSubtitleBindings;
import com.openapi.ks.myapp.translation.realtime.VideoRealtimeSubtitleSession;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

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

    private Unbinder unbinder;
    @BindView(R.id.music_detail_root)
    android.view.ViewGroup detailRoot;
    @BindView(R.id.music_detail_track_title)
    MarqueeTextView trackTitle;
    @BindView(R.id.music_detail_art)
    ImageView artView;
    @Nullable
    @BindView(R.id.progress)
    SeekBar progressView;
    @Nullable
    @BindView(R.id.iv_pre)
    android.widget.ImageView ivPre;
    @Nullable
    @BindView(R.id.iv_play)
    android.widget.ImageView ivPlay;
    @Nullable
    @BindView(R.id.iv_next)
    android.widget.ImageView ivNext;
    @Nullable
    @BindView(R.id.time)
    TextView tvTime;
    @Nullable
    @BindView(R.id.duration)
    TextView tvDuration;
    @Nullable
    @BindView(R.id.pb_loading)
    ProgressBar pbLoading;
    @BindView(R.id.my_favoriteview)
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
        setContentView(R.layout.layout_sound_detail);
        unbinder = ButterKnife.bind(this);
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

    @OnClick(R.id.music_detail_back)
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
        if (unbinder != null) {
            unbinder.unbind();
        }
        super.onDestroy();
    }
}
