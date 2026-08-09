package com.openapi.ks.myapp.video.vlc;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Message;
import android.text.TextUtils;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import com.openai.commonutil.LogUtil;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.cache.ICacheManager;
import com.shuyu.gsyvideoplayer.listener.GSYMediaPlayerListener;
import com.shuyu.gsyvideoplayer.model.GSYModel;
import com.shuyu.gsyvideoplayer.model.VideoOptionModel;
import com.shuyu.gsyvideoplayer.player.BasePlayerManager;
import com.shuyu.gsyvideoplayer.utils.CommonUtil;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import tv.danmaku.ijk.media.player.IMediaPlayer;

/**
 * GSY {@link com.shuyu.gsyvideoplayer.player.PlayerFactory} 用的 LibVLC 内核（VideoLAN）。
 */
public class VlcPlayerManager extends BasePlayerManager {

    private Context context;
    private LibVlcIjkMediaPlayer mediaPlayer;
    private long lastTotalRxBytes;
    private long lastTimeStamp;
    /** 最近一次 {@link #showDisplay(Message)} 给出的真实渲染 Surface（可能晚于 init） */
    private Surface mLastVideoSurface;
    /**
     * GSY {@link com.shuyu.gsyvideoplayer.GSYVideoBaseManager#initVideoPlayer} 传入的 context；
     * 经 {@code initContext} 后多为 Application，不能用于 {@code instanceof Activity}。
     * {@link #showDisplay} 里改为从 {@link GSYVideoManager#listener()}（一般为 GSYVideoView）解析 Activity。
     */
    private WeakReference<Context> mHostUiContextRef;

    @Override
    public IMediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    @Override
    public void initVideoPlayer(Context context, Message msg, List<VideoOptionModel> optionModelList,
                                ICacheManager cacheManager) {
        mHostUiContextRef = (context != null) ? new WeakReference<>(context) : null;
        this.context = context != null ? context.getApplicationContext() : null;
        mediaPlayer = new LibVlcIjkMediaPlayer(context);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        Surface initial = mLastVideoSurface;
        if (initial != null && !initial.isValid()) {
            initial = null;
            mLastVideoSurface = null;
        }
        /*
         * 不用 Media3 PlaceholderSurface：对部分 HLS/H264 组合 LibVLC 会 EncounteredError，
         * 而 Exo 正常。仅在有上一帧保留的合法 Surface 时绑定；否则由 prepareAsync 轮询与
         * {@link #showDisplay(Message)} 提供真实 TextureView Surface。
         */
        if (initial != null && initial.isValid()) {
            mediaPlayer.setSurface(initial);
        }
        GSYModel gsyModel = (GSYModel) msg.obj;
        String url = gsyModel.getUrl();
        BufferedInputStream videoBufferedInputStream = gsyModel.getVideoBufferedInputStream();
        try {
            /*
             * GSY ProxyCache 对不含“.m3u8”字面量的 HLS、或 .M3U8 等仍会走 127.0.0.1 代理，对多段 m3u8 常不可用；
             * LibVLC 自带 HLS，直接拉原地址更稳。
             */
            boolean useProxyCache = gsyModel.isCache() && cacheManager != null
                    && !LibVlcIjkMediaPlayer.isLikelyNetworkHls(url);
            if (useProxyCache) {
                cacheManager.doCacheLogic(context, mediaPlayer, url,
                        gsyModel.getMapHeadData(), gsyModel.getCachePath());
                mediaPlayer.bindMediaFromDataSource(gsyModel.getMapHeadData());
            } else {
                if (!TextUtils.isEmpty(url)) {
                    Uri uri = Uri.parse(url);
                    if (uri.getScheme() != null && (uri.getScheme().equals(ContentResolver.SCHEME_ANDROID_RESOURCE)
                            || uri.getScheme().equals("assets"))) {
                        mediaPlayer.setDataSource(url);
                        mediaPlayer.bindMediaFromDataSource(gsyModel.getMapHeadData());
                    } else if (uri.getScheme() != null && uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
                        mediaPlayer.setDataSourceFromContent(context, uri);
                        mediaPlayer.bindMediaFromDataSource(gsyModel.getMapHeadData());
                    } else {
                        mediaPlayer.setDataSource(url);
                        mediaPlayer.bindMediaFromDataSource(gsyModel.getMapHeadData());
                    }
                } else if (videoBufferedInputStream != null) {
                    mediaPlayer.setDataSource(url);
                    mediaPlayer.bindMediaFromDataSource(gsyModel.getMapHeadData());
                } else {
                    mediaPlayer.setDataSource(url);
                    mediaPlayer.bindMediaFromDataSource(gsyModel.getMapHeadData());
                }
            }
            mediaPlayer.setLooping(gsyModel.isLooping());
            if (gsyModel.getSpeed() != 1 && gsyModel.getSpeed() > 0) {
                mediaPlayer.setSpeed(gsyModel.getSpeed(), 1);
            }
        } catch (IOException e) {
            LogUtil.e(e);
        }
        initSuccess(gsyModel);
    }

    /**
     * 从当前 GSY 监听者（多为 View）解析前台 Activity；GSY 存的是 ApplicationContext 时仅靠 init 的 ref 拿不到窗口。
     */
    private Activity resolveHostActivity() {
        try {
            GSYMediaPlayerListener lst = GSYVideoManager.instance().listener();
            if (lst instanceof View) {
                Activity a = CommonUtil.scanForActivity(((View) lst).getContext());
                if (a != null) {
                    return a;
                }
            }
        } catch (Throwable ignored) {
        }
        Context ref = mHostUiContextRef != null ? mHostUiContextRef.get() : null;
        Activity fromRef = CommonUtil.scanForActivity(ref);
        if (fromRef != null) {
            return fromRef;
        }
        return CommonUtil.scanForActivity(context);
    }

    /**
     * decor 子树里已 layout、面积最大的 TextureView（GSY 输出面），尺寸与 Surface 缓冲一致，优于整屏 metrics。
     */
    private static TextureView findLargestLaidOutTextureView(View root) {
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        TextureView best = null;
        int bestArea = 0;
        Deque<View> stack = new ArrayDeque<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            View v = stack.removeLast();
            if (v instanceof TextureView) {
                int w = v.getWidth();
                int h = v.getHeight();
                if (w > 0 && h > 0) {
                    int area = w * h;
                    if (area > bestArea) {
                        bestArea = area;
                        best = (TextureView) v;
                    }
                }
            }
            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) v;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    stack.add(vg.getChildAt(i));
                }
            }
        }
        return best;
    }

    /** 优先 TextureView 像素，其次 decor；写入 out[0]/out[1]，可能为 0（尚未测量）。 */
    private static void readPreferredVideoOutputSize(Activity activity, int[] out) {
        out[0] = 0;
        out[1] = 0;
        if (activity == null) {
            return;
        }
        View decor = activity.getWindow().getDecorView();
        TextureView tv = findLargestLaidOutTextureView(decor);
        if (tv != null) {
            out[0] = tv.getWidth();
            out[1] = tv.getHeight();
            return;
        }
        out[0] = decor.getWidth();
        out[1] = decor.getHeight();
    }

    @Override
    public void showDisplay(Message msg) {
        if (msg.obj == null) {
            mLastVideoSurface = null;
            if (mediaPlayer != null) {
                mediaPlayer.onReleaseSurface();
            }
            return;
        }
        Surface s = (Surface) msg.obj;
        int viewW = msg.arg1;
        int viewH = msg.arg2;
        // GSY 12.x 有时传 1x1 占位值，视为无效
        if (viewW < 2) viewW = 0;
        if (viewH < 2) viewH = 0;
        Activity hostAct = resolveHostActivity();
        if ((viewW <= 0 || viewH <= 0) && hostAct != null) {
            int[] sz = new int[2];
            readPreferredVideoOutputSize(hostAct, sz);
            if (viewW <= 0 && sz[0] > 0) {
                viewW = sz[0];
            }
            if (viewH <= 0 && sz[1] > 0) {
                viewH = sz[1];
            }
        }
        mLastVideoSurface = (s != null && s.isValid()) ? s : null;
        if (mediaPlayer != null) {
            mediaPlayer.onShowDisplay(s, viewW, viewH);
            if (s != null && s.isValid() && hostAct != null && (viewW <= 0 || viewH <= 0)) {
                final Activity act = hostAct;
                final Runnable retry = () -> {
                    if (mediaPlayer == null) {
                        return;
                    }
                    int[] sz = new int[2];
                    readPreferredVideoOutputSize(act, sz);
                    if (sz[0] > 0 && sz[1] > 0) {
                        mediaPlayer.applyWindowSizeIfAttached(sz[0], sz[1]);
                    }
                };
                final View decor = act.getWindow().getDecorView();
                decor.post(retry);
                decor.post(() -> decor.post(retry));
            }
        }
    }

    @Override
    public void setSpeed(float speed, boolean soundTouch) {
        if (mediaPlayer != null) {
            mediaPlayer.setSpeed(speed, 1);
        }
    }

    @Override
    public void setNeedMute(boolean needMute) {
        if (mediaPlayer != null) {
            if (needMute) {
                mediaPlayer.setVolume(0, 0);
            } else {
                mediaPlayer.setVolume(1, 1);
            }
        }
    }

    @Override
    public void setVolume(float left, float right) {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(left, right);
        }
    }

    @Override
    public void releaseSurface() {
        // GSY 的 releaseSurface 走 Handler，可能晚于 showDisplay（直接调用）到达。
        // 若 showDisplay 已设了新的有效 Surface，不要清掉它。
        if (mLastVideoSurface != null && mLastVideoSurface.isValid()) {
            return;
        }
        mLastVideoSurface = null;
        if (mediaPlayer != null) {
            mediaPlayer.onReleaseSurface();
        }
    }

    @Override
    public void release() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        lastTotalRxBytes = 0;
        lastTimeStamp = 0;
    }

    @Override
    public int getBufferedPercentage() {
        return -1;
    }

    @Override
    public long getNetSpeed() {
        return getNetSpeed(context);
    }

    @Override
    public void setSpeedPlaying(float speed, boolean soundTouch) {
        setSpeed(speed, soundTouch);
    }

    @Override
    public void start() {
        if (mediaPlayer != null) {
            mediaPlayer.start();
        }
    }

    @Override
    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }

    @Override
    public void pause() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
        }
    }

    @Override
    public int getVideoWidth() {
        if (mediaPlayer != null) {
            return mediaPlayer.getVideoWidth();
        }
        return 0;
    }

    @Override
    public int getVideoHeight() {
        if (mediaPlayer != null) {
            return mediaPlayer.getVideoHeight();
        }
        return 0;
    }

    @Override
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    @Override
    public void seekTo(long time) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(time);
        }
    }

    @Override
    public long getCurrentPosition() {
        if (mediaPlayer != null) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public long getDuration() {
        if (mediaPlayer != null) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }

    @Override
    public int getVideoSarNum() {
        if (mediaPlayer != null) {
            return mediaPlayer.getVideoSarNum();
        }
        return 1;
    }

    @Override
    public int getVideoSarDen() {
        if (mediaPlayer != null) {
            return mediaPlayer.getVideoSarDen();
        }
        return 1;
    }

    @Override
    public boolean isSurfaceSupportLockCanvas() {
        return false;
    }

    private long getNetSpeed(Context context) {
        if (context == null) {
            return 0;
        }
        long nowTotalRxBytes = TrafficStats.getUidRxBytes(context.getApplicationInfo().uid)
                == TrafficStats.UNSUPPORTED ? 0 : (TrafficStats.getTotalRxBytes() / 1024);
        long nowTimeStamp = System.currentTimeMillis();
        long calculationTime = (nowTimeStamp - lastTimeStamp);
        if (calculationTime == 0) {
            return 0;
        }
        long speed = ((nowTotalRxBytes - lastTotalRxBytes) * 1000 / calculationTime);
        lastTimeStamp = nowTimeStamp;
        lastTotalRxBytes = nowTotalRxBytes;
        return speed;
    }
}
