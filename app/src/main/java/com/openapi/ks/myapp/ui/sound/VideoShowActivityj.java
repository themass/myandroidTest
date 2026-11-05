package com.openapi.ks.myapp.ui.sound;


import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.openapi.core.util.LogUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.cast.CastPlaySource;
import com.openapi.ks.myapp.cast.GoogleCastSessionController;
import com.openapi.ks.myapp.data.VideoUtil;
import com.openapi.ks.myapp.ui.gsy.DetailGSYVideoPlayer;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.utils.OrientationUtils;
import com.openapi.ks.chat.databinding.LayoutVideoShowBinding;


/**
 * Created by openapi on 2015/9/1.
 */
public class VideoShowActivityj extends AppCompatActivity {
    public DetailGSYVideoPlayer videoPlayer;
    private GoogleCastSessionController castSessionController;
    RecommendVo vo;

    private void bindButterViews() {
        LayoutVideoShowBinding b = LayoutVideoShowBinding.bind(findViewById(android.R.id.content));
        videoPlayer = b.jzVideo;
    }

    private void unbindButterViews() {
        videoPlayer = null;
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
        VideoPlaybackUiHelper.maybeShowFirstPlaybackGestureHint(this, videoPlayer);

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
    public void onPause() {
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
    }

    @Override
    protected void onNewIntent(Intent intent) {
        //LogUtil.i(getClass().getSimpleName() + "-onNewIntent");
        super.onNewIntent(intent);
    }

    protected boolean enableSliding() {
        return true;
    }
}
