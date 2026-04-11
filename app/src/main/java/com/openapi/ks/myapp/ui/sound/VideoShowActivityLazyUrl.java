package com.openapi.ks.myapp.ui.sound;


import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.VolleyError;
import com.openai.commonview.translation.TranslationOverlaySession;
import com.openai.commonview.translation.VideoTranslationRegistry;
import com.openai.commonview.translation.VideoTranslationRequest;
import com.openai.commonview.view.MyFavoriteView;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.ToastUtil;
import com.openai.net.request.CommonResponse;
import com.openapi.ks.chat.R;
import com.openai.commonevent.FavoriteVo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openai.net.BaseService;
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
public class VideoShowActivityLazyUrl extends AppCompatActivity implements MyFavoriteView.OnFavoriteItemClick {

    private static final String VIDEO_TAG = "video_url_tag";
    private Unbinder unbinder;
    @BindView(R.id.jz_video)
    public DetailGSYVideoPlayer videoPlayer;
    private GoogleCastSessionController castSessionController;
    RecommendVo vo;
    @BindView(R.id.my_favoriteview)
    MyFavoriteView myFavoriteView;
    protected BaseService indexService;
    private String url;
    /** 懒加载 URL 时保留至首次 {@link #openVideoShow}，用于恢复进度 */
    private Bundle playbackSavedState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playbackSavedState = savedInstanceState;
        setContentView(R.layout.layout_video_show);
        unbinder = ButterKnife.bind(this);
        indexService = new BaseService();
        indexService.setup(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vo = getIntent().getSerializableExtra(Constants.CONFIG_PARAM, RecommendVo.class);
        } else {
            vo = (RecommendVo) getIntent().getSerializableExtra(Constants.CONFIG_PARAM);
        }
        url = vo.actionUrl;
        LogUtil.i("data=" + vo);
        if (vo.needLazyUrl) {
            try {
                ToastUtil.showShort(R.string.load);
                videoPlayer.loadingInit();
                indexService.getData(Constants.getUrlWithParam(Constants.API_VIDEO_ITEM_URL, vo.id), new CommonResponse.ResponseOkListener<RecommendVo>() {
                    @Override
                    public void onResponse(RecommendVo o) {
                        super.onResponse(o);
                        openVideoShow(o);
                        videoPlayer.closeLoadingInit();
                    }
                }, new CommonResponse.ResponseErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                        super.onErrorResponse(volleyError);
                        LogUtil.e(volleyError.getMessage(), volleyError);
                        videoPlayer.closeLoadingInit();
                        openVideoShow(vo);
                    }
                }, VIDEO_TAG, RecommendVo.class);

            } catch (Exception e) {
                videoPlayer.closeLoadingInit();
                openVideoShow(vo);
            }
        } else {
            openVideoShow(vo);
        }
        videoPlayer.loadPoster(this, vo.img);
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
                if (url == null || url.isEmpty()) {
                    throw new IllegalStateException("play url not ready");
                }
                builder.setVideoUrl(url)
                        .setHttpHeaders(VideoUtil.headersForRecommend(vo));
            }

            @Override
            public VideoTranslationRegistry registry() {
                return VideoTranslationRegistry.getInstance();
            }
        });

        videoPlayer.installGoogleCastRouteButton(this);
        castSessionController = new GoogleCastSessionController(this, videoPlayer, new CastPlaySource() {
            @Override
            public String getPlayUrl() {
                return url;
            }

            @Override
            public String getTitle() {
                return vo != null ? vo.title : null;
            }
        });
    }

    private void openVideoShow(RecommendVo urlVo) {
        url = VideoUtil.buildPlayUrl(urlVo);
        videoPlayer.setUp(url, true, null, VideoUtil.headersForRecommend(urlVo), vo.title);
        VideoShowPlaybackStateHelper.prepareSeekRestore(videoPlayer, playbackSavedState);
        videoPlayer.startPlayLogic();
        VideoShowPlaybackStateHelper.schedulePauseIfWasPaused(videoPlayer, playbackSavedState);
        playbackSavedState = null;
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
        indexService.cancelRequest(VIDEO_TAG);
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
        return url;
    }
}
