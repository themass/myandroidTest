package com.openapi.feature.video.page;


import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.openapi.common.net.http.NetError;
import com.openapi.speech.subtitle.api.TranslationOverlaySession;
import com.openapi.speech.subtitle.api.VideoTranslationRegistry;
import com.openapi.speech.subtitle.api.VideoTranslationRequest;
import com.openapi.core.favorite.MyFavoriteView;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.common.net.request.CommonResponse;
import com.openapi.feature.video.R;
import com.openapi.core.db.FavoriteVo;
import com.openapi.core.model.RecommendVo;
import com.openapi.core.config.Constants;
import com.openapi.common.net.http.BaseService;
import com.openapi.feature.video.cast.CastPlaySource;
import com.openapi.feature.video.cast.GoogleCastSessionController;
import com.openapi.feature.video.data.VideoUtil;
import com.openapi.feature.video.player.DetailGSYVideoPlayer;
import com.openapi.feature.video.subtitle.realtime.RealtimeSubtitleBindings;
import com.openapi.feature.video.subtitle.local.LocalPlaybackTools;
import com.openapi.speech.subtitle.realtime.VideoRealtimeSubtitleSession;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.utils.OrientationUtils;
import com.openapi.feature.video.databinding.LayoutVideoShowBinding;
import android.widget.Toast;
import android.net.Uri;
import android.provider.MediaStore;
import android.database.Cursor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import com.openapi.cast.JavaCaster;
import com.openapi.cast.DlnaDiscoveryCallback;
import com.openapi.cast.DlnaDevice;
import com.openapi.common.ui.dialog.AppDialogHost;


/**
 * Created by openapi on 2015/9/1.
 */
public class VideoShowActivityLazyUrl extends AppCompatActivity implements MyFavoriteView.OnFavoriteItemClick {

    private static final String VIDEO_TAG = "video_url_tag";
    public DetailGSYVideoPlayer videoPlayer;
    private GoogleCastSessionController castSessionController;
    private DlnaDiscoveryCallback dlnaDiscovery;
    private JavaCaster dlnaCaster;
    RecommendVo vo;
    MyFavoriteView myFavoriteView;
    protected BaseService indexService;
    private String url;
    /** 懒加载 URL 时保留至首次 {@link #openVideoShow}，用于恢复进度 */
    private Bundle playbackSavedState;
    private LocalPlaybackTools localTools;
    private final ActivityResultLauncher<String[]> subtitlePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (localTools != null) localTools.importSubtitle(uri);
            });

    private void bindButterViews() {
        LayoutVideoShowBinding b = LayoutVideoShowBinding.bind(findViewById(android.R.id.content));
        videoPlayer = b.jzVideo;
        // 布局里的 MyFavoriteView 保持 GONE；工具条挂在播放器上以便全屏克隆
        myFavoriteView = videoPlayer.getMyFavoriteView();
        // 播放器控制条收起后唯一的返回入口，常驻可见
        b.btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playbackSavedState = savedInstanceState;
        setContentView(R.layout.layout_video_show);
        bindButterViews();
        videoPlayer.ensureOrientationUtils(this);
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
                    public void onErrorResponse(NetError volleyError) {
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
        // 右侧栏：译/倍速/收藏；本地隐藏外线 WWW；content:// 再隐藏「复制链接」分享图标
        boolean local = VideoUtil.isLocalPlayUrl(vo != null ? vo.actionUrl : null);
        if (local) {
            myFavoriteView.showLocalFilePlayback(false);
        } else {
            myFavoriteView.showOnlineVideoPlayback();
        }
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

        VideoRealtimeSubtitleSession realtimeSession = RealtimeSubtitleBindings.forGsyPlayer(
                this,
                videoPlayer,
                videoPlayer,
                () -> url != null ? url : "",
                () -> VideoUtil.headersForRecommend(vo));
        RealtimeSubtitleBindings.attachVideo(myFavoriteView, realtimeSession, videoPlayer);

        boolean prototype = local && !vo.needLazyUrl
                && (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (prototype) {
            localTools = new LocalPlaybackTools(this, videoPlayer,
                    () -> subtitlePicker.launch(new String[]{"*/*"}), this::showDlnaDevices);
        } else {
            videoPlayer.installGoogleCastRouteButton(this);
        }
        // 投屏入口封装在右下角 favor 工具条；发现/投屏仍由本页实现
        myFavoriteView.setCastClickListener(v -> showDlnaDevices());
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
        boolean local = VideoUtil.isLocalPlayUrl(url);
        // 本地 content:// 不能走 ProxyCache，也不要带 Referer/UA（Exo 对本地 URI 易被错误头干扰）
        boolean cache = !local;
        java.util.Map<String, String> headers = local ? null : VideoUtil.headersForRecommend(urlVo);
        videoPlayer.setUp(url, cache, null, headers, vo.title);
        VideoShowPlaybackStateHelper.prepareSeekRestore(videoPlayer, playbackSavedState);
        videoPlayer.startPlayLogic();
        videoPlayer.enableOrientationAfterPrepared();
        VideoShowPlaybackStateHelper.schedulePauseIfWasPaused(videoPlayer, playbackSavedState);
        playbackSavedState = null;
        VideoPlaybackUiHelper.maybeShowFirstPlaybackGestureHint(this, videoPlayer);
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
        if (localTools != null) localTools.pause();
        if (castSessionController != null) {
            castSessionController.onPause();
        }
        if (videoPlayer != null) {
            videoPlayer.setOrientationPaused(true);
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
        if (localTools != null) localTools.resume();
        try {
            GSYVideoManager.onResume();
        } catch (Throwable e) {
            LogUtil.e(e);
        }
        if (videoPlayer != null) {
            videoPlayer.setOrientationPaused(false);
        }
        if (castSessionController != null) {
            castSessionController.onResume();
        }
    }

    @Override
    public void onBackPressed() {
        if (videoPlayer != null) {
            videoPlayer.backToPortraitIfNeeded();
        }
        if (GSYVideoManager.backFromWindowFull(this)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackPressedDispatcher().onBackPressed();
        } else {
            super.onBackPressed();
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
        String playUrl = url;
        if (!com.openapi.common.util.lang.StringUtils.hasText(playUrl)) {
            Toast.makeText(this, R.string.cast_url_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        String title = vo != null ? vo.title : null;
        if (VideoUtil.isLocalPlayUrl(playUrl)) {
            String path = resolveLocalPathForCast(playUrl);
            if (path == null) {
                Toast.makeText(this, "本地视频无法投屏：找不到可读文件", Toast.LENGTH_SHORT).show();
                return;
            }
            dlnaCaster.castLocal(device, path, title, error -> runOnUiThread(() -> {
                if (error != null) {
                    Toast.makeText(this, "投屏失败：" + error, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "已投屏到 " + device.getFriendlyName(), Toast.LENGTH_SHORT).show();
                }
            }));
            return;
        }
        dlnaCaster.castRemote(device, playUrl, title, error -> runOnUiThread(() -> {
            if (error != null) {
                Toast.makeText(this, "投屏失败：" + error, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已投屏到 " + device.getFriendlyName(), Toast.LENGTH_SHORT).show();
            }
        }));
    }

    /** content:// 尽量解析成真实路径；不行就复制到 cache，供 LocalMediaServer 托管。 */
    private String resolveLocalPathForCast(String playUrl) {
        try {
            if (playUrl.startsWith("/")) {
                File f = new File(playUrl);
                return f.canRead() ? f.getAbsolutePath() : null;
            }
            if (playUrl.startsWith("file://")) {
                File f = new File(Uri.parse(playUrl).getPath());
                return f.canRead() ? f.getAbsolutePath() : null;
            }
            Uri uri = Uri.parse(playUrl);
            // MediaStore 常见 _data 列（API 31 上仍可读本应用有权访问的媒体）
            try (Cursor c = getContentResolver().query(uri, new String[]{MediaStore.Video.Media.DATA}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    String data = c.getString(0);
                    if (data != null && new File(data).canRead()) {
                        return data;
                    }
                }
            } catch (Exception ignored) {
            }
            File out = new File(getCacheDir(), "cast_local_" + Math.abs(playUrl.hashCode()) + ".mp4");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(out)) {
                if (in == null) return null;
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    fos.write(buf, 0, n);
                }
            }
            return out.getAbsolutePath();
        } catch (Exception e) {
            LogUtil.e(e);
            return null;
        }
    }

    @Override
    public void onDestroy() {
        if (localTools != null) localTools.close();
        if (myFavoriteView != null) {
            myFavoriteView.releaseTranslationOverlay();
        }
        if (dlnaCaster != null) {
            dlnaCaster.close();
        }
        if (dlnaDiscovery != null) {
            dlnaDiscovery.close();
        }
        indexService.cancelRequest(VIDEO_TAG);
        if (videoPlayer != null) {
            videoPlayer.releaseOrientationUtils();
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
        return url;
    }
}
