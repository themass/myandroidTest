package com.openapi.ks.myapp.video.page;


import android.widget.Toast;
import com.openapi.core.cast.JavaCaster;
import com.openapi.core.cast.DlnaDiscoveryCallback;
import com.openapi.core.cast.DlnaDevice;
import android.widget.ImageButton;
import com.openapi.common.design.dialog.AppDialogHost;
import com.shuyu.gsyvideoplayer.video.base.GSYVideoView;
import android.util.Rational;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;

import com.openapi.core.event.FavoriteVo;

import com.openapi.ks.myapp.translation.api.TranslationOverlaySession;
import com.openapi.ks.myapp.translation.api.VideoTranslationRegistry;
import com.openapi.ks.myapp.translation.api.VideoTranslationRequest;
import com.openapi.core.favorite.MyFavoriteView;
import com.openapi.common.util.log.LogUtil;
import com.openapi.feature.video.R;
import com.openapi.core.model.RecommendVo;
import com.openapi.core.config.Constants;
import com.openapi.ks.myapp.cast.CastPlaySource;
import com.openapi.ks.myapp.cast.GoogleCastSessionController;
import com.openapi.ks.myapp.data.VideoUtil;
import com.openapi.ks.myapp.video.player.DetailGSYVideoPlayer;
import com.openapi.ks.myapp.translation.realtime.RealtimeSubtitleBindings;
import com.openapi.ks.myapp.translation.realtime.VideoRealtimeSubtitleSession;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.utils.OrientationUtils;
import com.openapi.feature.video.databinding.LayoutVideoShowBinding;


/**
 * Created by openapi on 2015/9/1.
 */
public class VideoShowActivity extends AppCompatActivity implements MyFavoriteView.OnFavoriteItemClick {
    public DetailGSYVideoPlayer videoPlayer;
    private DlnaDiscoveryCallback dlnaDiscovery;
    private JavaCaster dlnaCaster;
    private boolean enteringPip;
    private GoogleCastSessionController castSessionController;
    RecommendVo vo;
    MyFavoriteView myFavoriteView;
    private android.widget.ImageButton dlnaCastButton;

    private void bindButterViews() {
        LayoutVideoShowBinding b = LayoutVideoShowBinding.bind(findViewById(android.R.id.content));
        videoPlayer = b.jzVideo;
        dlnaCastButton = b.btnDlnaCast;
        myFavoriteView = b.myFavoriteview;
    }

    private void unbindButterViews() {
        videoPlayer = null;
        dlnaCastButton = null;
        myFavoriteView = null;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_video_show);
        bindButterViews();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vo = getIntent().getSerializableExtra(Constants.CONFIG_PARAM, RecommendVo.class);
        } else {
            vo = (RecommendVo) getIntent().getSerializableExtra(Constants.CONFIG_PARAM);
        }
        String playUrl = VideoUtil.buildPlayUrl(vo);
        videoPlayer.setUp(playUrl, true, null, VideoUtil.headersForRecommend(vo), vo.title);
        VideoShowPlaybackStateHelper.prepareSeekRestore(videoPlayer, savedInstanceState);
        videoPlayer.loadPoster(this, vo.img);
        videoPlayer.startPlayLogic();
        VideoShowPlaybackStateHelper.schedulePauseIfWasPaused(videoPlayer, savedInstanceState);

        videoPlayer.installGoogleCastRouteButton(this);
        castSessionController = new GoogleCastSessionController(this, videoPlayer, new CastPlaySource() {
            @Override
            public String getPlayUrl() {
                return VideoUtil.buildPlayUrl(vo);
            }

            @Override
            public String getTitle() {
                return vo != null ? vo.title : null;
            }
        });

        dlnaCastButton.setOnClickListener(v -> showDlnaDevices());
        myFavoriteView.setListener(this);
        myFavoriteView.initFavoriteBackGroud(vo.actionUrl);
        myFavoriteView.showVideoLocal();
        VideoPlaybackUiHelper.maybeShowFirstPlaybackGestureHint(this, videoPlayer);

        myFavoriteView.setTranslationSession(new TranslationOverlaySession() {
            @Override
            public ViewGroup getOverlayParent() {
                return videoPlayer;
            }

            @Override
            public void fillRequest(VideoTranslationRequest.Builder builder) {
                builder.setVideoUrl(VideoUtil.buildPlayUrl(vo))
                        .setHttpHeaders(VideoUtil.headersForRecommend(vo));
            }

            @Override
            public VideoTranslationRegistry registry() {
                return VideoTranslationRegistry.getInstance();
            }
        });

        VideoRealtimeSubtitleSession realtimeSession = RealtimeSubtitleBindings.forGsyPlayer(
                this,
                videoPlayer,
                videoPlayer,
                () -> VideoUtil.buildPlayUrl(vo),
                () -> VideoUtil.headersForRecommend(vo));
        RealtimeSubtitleBindings.attachVideo(myFavoriteView, realtimeSession, videoPlayer);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        VideoShowPlaybackStateHelper.save(videoPlayer, outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (videoPlayer != null) {
            OrientationUtils ou = videoPlayer.getOrientationUtilsForActivity();
            if (ou != null) {
                videoPlayer.onConfigurationChanged(this, newConfig, ou);
            }
        }
    }

    @Override
    protected void onPause() {
        if (castSessionController != null) {
            castSessionController.onPause();
        }
        super.onPause();
        if (enteringPip) {
            return;
        }
        try {
            GSYVideoManager.onPause();
        } catch (Throwable e) {
            LogUtil.e(e);
        }
    }

    private void showDlnaDevices() {
        if (dlnaDiscovery == null) {
            dlnaDiscovery = new DlnaDiscoveryCallback();
        }
        Toast.makeText(this, "正在搜索电视…", Toast.LENGTH_SHORT).show();
        dlnaDiscovery.discover(devices -> runOnUiThread(() -> {
            if (devices.isEmpty()) {
                Toast.makeText(this, "未发现可投屏的设备", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] names = new String[devices.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = devices.get(i).getFriendlyName();
            }
            AppDialogHost.showItems(this, "投屏到", names, true,
                    which -> castToDlna(devices.get(which)));
        }));
    }

    private void castToDlna(DlnaDevice device) {
        if (dlnaCaster == null) {
            dlnaCaster = new JavaCaster(this);
        }
        String url = VideoUtil.buildPlayUrl(vo);
        dlnaCaster.castRemote(device, url, vo.title, error -> runOnUiThread(() -> {
            if (error != null) {
                Toast.makeText(this, "投屏失败：" + error, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已投屏到 " + device.getFriendlyName(), Toast.LENGTH_SHORT).show();
            }
        }));
    }

    /** 正在播放时离开页面，进入系统画中画小窗；视频不暂停。 */
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (videoPlayer == null || videoPlayer.getCurrentState() != GSYVideoView.CURRENT_STATE_PLAYING) {
            return;
        }
        enteringPip = true;
        enterPictureInPictureMode(new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9))
                .build());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        enteringPip = isInPictureInPictureMode;
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            GSYVideoManager.onResume();
        } catch (Throwable e) {
            LogUtil.e(e);
        }
        if (castSessionController != null) {
            castSessionController.onResume();
        }
    }

    @Override
    public void onBackPressed() {
        if (GSYVideoManager.backFromWindowFull(this)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackPressedDispatcher().onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        if (myFavoriteView != null) {
            myFavoriteView.releaseTranslationOverlay();
        }
        if (dlnaCaster != null) {
            dlnaCaster.close();
        }
        if (dlnaDiscovery != null) {
            dlnaDiscovery.close();
        }
        GSYVideoManager.releaseAllVideos();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        //LogUtil.i(getClass().getSimpleName() + "-onNewIntent");
        super.onNewIntent(intent);
    }

    protected boolean enableSliding() {
        return true;
    }

    @Override
    public FavoriteVo getFavoriteDataUrl() {
        return vo.tofavorite(Constants.FavoriteType.VIDEO);
    }

    @Override
    public boolean setSpeed(float speed) {
        videoPlayer.setSpeed(speed, true);
        return true;
    }

    @Override
    public String getBrowserDataUrl() {
        return VideoUtil.buildPlayUrl(vo);
    }
}
