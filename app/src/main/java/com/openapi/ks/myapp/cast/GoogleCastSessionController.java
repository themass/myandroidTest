package com.openapi.ks.myapp.cast;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.ResultCallback;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.ToastUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.ui.gsy.DetailGSYVideoPlayer;

/**
 * 详情页 Google Cast：连接成功后把当前片源 URL 交给电视端 Default Receiver 播放，并暂停手机端 GSY。
 * <p>
 * 限制：标准 Receiver 无法携带 GSY 的自定义 HTTP Header；若 CDN 强校验 Header，需自建 Receiver 或改服务端。
 * </p>
 */
public final class GoogleCastSessionController {

    private final AppCompatActivity activity;
    private final DetailGSYVideoPlayer player;
    private final CastPlaySource source;
    private SessionManager sessionManager;

    private final SessionManagerListener<CastSession> sessionListener =
            new SessionManagerListener<CastSession>() {
                @Override
                public void onSessionStarting(@NonNull CastSession castSession) {
                }

                @Override
                public void onSessionStarted(@NonNull CastSession castSession, @NonNull String sessionId) {
                    loadOntoRemote(castSession);
                }

                @Override
                public void onSessionStartFailed(@NonNull CastSession castSession, int error) {
                    LogUtil.i("Cast onSessionStartFailed error=" + error);
                }

                @Override
                public void onSessionEnding(@NonNull CastSession castSession) {
                }

                @Override
                public void onSessionEnded(@NonNull CastSession castSession, int error) {
                }

                @Override
                public void onSessionResuming(@NonNull CastSession castSession, @NonNull String sessionId) {
                }

                @Override
                public void onSessionResumed(@NonNull CastSession castSession, boolean wasSuspended) {
                }

                @Override
                public void onSessionResumeFailed(@NonNull CastSession castSession, int error) {
                }

                @Override
                public void onSessionSuspended(@NonNull CastSession castSession, int reason) {
                }
            };

    public GoogleCastSessionController(@NonNull AppCompatActivity activity,
            @NonNull DetailGSYVideoPlayer player,
            @NonNull CastPlaySource source) {
        this.activity = activity;
        this.player = player;
        this.source = source;
    }

    public void onResume() {
        try {
            CastContext.getSharedInstance(activity);
            sessionManager = CastContext.getSharedInstance(activity).getSessionManager();
            sessionManager.addSessionManagerListener(sessionListener, CastSession.class);
        } catch (Throwable t) {
            LogUtil.e(t);
        }
    }

    public void onPause() {
        try {
            if (sessionManager != null) {
                sessionManager.removeSessionManagerListener(sessionListener, CastSession.class);
            }
        } catch (Throwable t) {
            LogUtil.e(t);
        }
        sessionManager = null;
    }

    private void loadOntoRemote(@NonNull CastSession session) {
        String url = source.getPlayUrl();
        if (TextUtils.isEmpty(url)) {
            ToastUtil.showShort(activity.getString(R.string.cast_url_not_ready));
            return;
        }
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            ToastUtil.showShort(activity.getString(R.string.cast_need_http_url));
            return;
        }
        RemoteMediaClient client = session.getRemoteMediaClient();
        if (client == null) {
            ToastUtil.showShort(activity.getString(R.string.cast_load_failed));
            return;
        }
        String title = source.getTitle();
        MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        if (!TextUtils.isEmpty(title)) {
            metadata.putString(MediaMetadata.KEY_TITLE, title);
        }
        MediaInfo mediaInfo = new MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(guessContentType(url))
                .setMetadata(metadata)
                .build();
        long pos = 0L;
        try {
            pos = Math.max(0L, player.getCurrentPositionWhenPlaying());
        } catch (Throwable ignored) {
        }
        long startPos = pos;
        client.load(mediaInfo, true, startPos).setResultCallback(
                new ResultCallback<RemoteMediaClient.MediaChannelResult>() {
                    @Override
                    public void onResult(@NonNull RemoteMediaClient.MediaChannelResult result) {
                        if (!result.getStatus().isSuccess()) {
                            ToastUtil.showShort(activity.getString(R.string.cast_load_failed));
                            return;
                        }
                        try {
                            player.onVideoPause();
                        } catch (Throwable t) {
                            LogUtil.e(t);
                        }
                    }
                });
    }

    /** 与 Default Media Receiver 常见类型对齐；异常时可再微调。 */
    public static String guessContentType(@NonNull String url) {
        String u = url.toLowerCase();
        if (u.contains(".m3u8") || u.contains("m3u8")) {
            return "application/x-mpegURL";
        }
        if (u.contains(".mpd")) {
            return "application/dash+xml";
        }
        return "video/mp4";
    }
}
