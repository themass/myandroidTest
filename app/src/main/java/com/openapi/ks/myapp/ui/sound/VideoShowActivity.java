package com.openapi.ks.myapp.ui.sound;


import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;

import com.openai.commonevent.FavoriteVo;

import com.openai.commonview.translation.TranslationOverlaySession;
import com.openai.commonview.translation.VideoTranslationRegistry;
import com.openai.commonview.translation.VideoTranslationRequest;
import com.openai.commonview.view.MyFavoriteView;
import com.openai.commonutil.LogUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.cast.CastPlaySource;
import com.openapi.ks.myapp.cast.GoogleCastSessionController;
import com.openapi.ks.myapp.data.VideoUtil;
import com.openapi.ks.myapp.ui.gsy.DetailGSYVideoPlayer;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.utils.OrientationUtils;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Created by openapi on 2015/9/1.
 */
public class VideoShowActivity extends AppCompatActivity implements MyFavoriteView.OnFavoriteItemClick {
    private Unbinder unbinder;
    @BindView(R.id.jz_video)
    public DetailGSYVideoPlayer videoPlayer;
    private GoogleCastSessionController castSessionController;
    RecommendVo vo;
    @BindView(R.id.my_favoriteview)
    MyFavoriteView myFavoriteView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_video_show);
        unbinder = ButterKnife.bind(this);

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

        myFavoriteView.setListener(this);
        myFavoriteView.initFavoriteBackGroud(vo.actionUrl);
        myFavoriteView.showVideoLocal();
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
        try {
            GSYVideoManager.onPause();
        } catch (Throwable e) {
            LogUtil.e(e);
        }
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
        GSYVideoManager.releaseAllVideos();
        super.onDestroy();
        unbinder.unbind();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        LogUtil.i(getClass().getSimpleName() + "-onNewIntent");
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
