package com.openapi.ks.myapp.video.vlc;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.MainThread;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.openai.commonutil.LogUtil;

import tv.danmaku.ijk.media.player.AbstractMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.MediaInfo;
import tv.danmaku.ijk.media.player.misc.IMediaDataSource;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;

/**
 * 将 LibVLC {@link org.videolan.libvlc.MediaPlayer} 适配为 GSY 使用的 {@link IMediaPlayer}。
 *
 * <p>GSY 常在 {@code prepareAsync()} 之后才通过 {@code showDisplay} 给出 TextureView 的 Surface，
 * 且可能晚于数秒。若一直等 Surface 再 {@code play()}，会长期无 {@code onPrepared}；若超时 {@code notifyOnError}，
 * 则表现为「加载失败」。因此策略为：<b>prepareAsync 即 {@code play()} 先拉流</b>，Surface 晚到再在
 * {@link #onShowDisplay} 里 {@code tryAttachSurface}。</p>
 *
 * <p>用 {@code mPlayLaunched} 防止重复 {@code play()}；之后 {@code onShowDisplay} 负责绑定/更换 Surface。</p>
 */
public class LibVlcIjkMediaPlayer extends AbstractMediaPlayer {

    /** adb logcat -s VLC_DBG 可单独过滤所有诊断日志，无需 myTag 开关 */
    private static final String TAG = "VLC_DBG";

    private final Context mAppContext;
    private final Handler mMainHandler;
    private final LibVLC mLibVlc;
    private final org.videolan.libvlc.MediaPlayer mVlcPlayer;

    private String mDataSource;
    private Map<String, String> mDataSourceHeadersFromProxy;
    private ParcelFileDescriptor mContentFd;
    private boolean mLooping;
    private float mRate = 1f;
    private Surface mSurface;
    /** 视频输出区域像素宽高，由 {@link VlcPlayerManager#showDisplay} 写入（优先 TextureView，与 Surface 缓冲一致） */
    private int mViewWidth;
    private int mViewHeight;
    private volatile boolean mReleased;
    private boolean mPreparedSent;
    private boolean mSeekPending;
    private int mBufferPercent = -1;
    private int mVideoW;
    private int mVideoH;
    private int mSarNum = 1;
    private int mSarDen = 1;

    /** play() 已调用过一次；防止重复启动 */
    private boolean mPlayLaunched;

    public LibVlcIjkMediaPlayer(Context context) {
        mAppContext = context.getApplicationContext();
        mMainHandler = new Handler(Looper.getMainLooper());
        mLibVlc = LibVlcHolder.get(mAppContext);
        mVlcPlayer = new org.videolan.libvlc.MediaPlayer(mLibVlc);
        mVlcPlayer.setEventListener(this::onVlcEventNative);
        Log.d(TAG, "LibVlcIjkMediaPlayer created");
    }

    // -------------------------------------------------------------------------
    // Surface 绑定与视频输出
    // -------------------------------------------------------------------------

    /**
     * 将当前 mSurface 绑定到 IVLCVout。
     * <p>
     * VLC 3.6 的 {@code MediaPlayer} 内部已在 {@code AWindow.SurfaceCallback} 里自动处理
     * vout 的 {@code play()} / {@code setVideoTrackEnabled(true/false)}：
     * <ul>
     *   <li>{@code onSurfacesCreated}: 若 {@code !mPlaying && mPlayRequested} → 自动 {@code play()}；
     *       否则若 {@code mVoutCount==0} → 自动 {@code setVideoTrackEnabled(true)}</li>
     *   <li>{@code onSurfacesDestroyed}: 若 {@code mVoutCount>0} → 自动 {@code setVideoTrackEnabled(false)}</li>
     * </ul>
     * 因此这里<b>只做 Surface 绑定</b>，不手动调 play / pause / setVideoTrackEnabled，
     * 避免与 VLC 内部状态机冲突导致黑屏。
     */
    private void tryAttachSurface(String caller) {
        if (mSurface == null || !mSurface.isValid()) {
            Log.w(TAG, "tryAttachSurface(" + caller + ") – surface invalid, skip");
            return;
        }
        int viewW = mViewWidth;
        int viewH = mViewHeight;
        if (viewW < 2 || viewH < 2) {
            android.util.DisplayMetrics dm = mAppContext.getResources().getDisplayMetrics();
            viewW = dm.widthPixels;
            viewH = dm.heightPixels;
        }
        boolean wasPlaying = false;
        int voutCount = -1;
        int videoTrackCount = -1;
        try { wasPlaying = mVlcPlayer.isPlaying(); } catch (Throwable ignored) {}
        try { voutCount = mVlcPlayer.getVLCVout().areViewsAttached() ? 1 : 0; } catch (Throwable ignored) {}
        try { videoTrackCount = mVlcPlayer.getVideoTracksCount(); } catch (Throwable ignored) {}
        Log.d(TAG, "tryAttachSurface(" + caller + ") BEGIN"
                + " surface=" + mSurface + " surfaceHash=" + System.identityHashCode(mSurface)
                + " isPlaying=" + wasPlaying
                + " viewsAttached=" + voutCount
                + " videoTrackCount=" + videoTrackCount
                + " mPlayLaunched=" + mPlayLaunched
                + " mViewSize=" + mViewWidth + "x" + mViewHeight
                + " resolvedSize=" + viewW + "x" + viewH);
        try {
            IVLCVout out = mVlcPlayer.getVLCVout();
            if (out.areViewsAttached()) {
                Log.d(TAG, "tryAttachSurface(" + caller + "): detachViews...");
                out.detachViews();
                Log.d(TAG, "tryAttachSurface(" + caller + "): detachViews done");
            }
            out.setVideoSurface(mSurface, null);
            out.setWindowSize(viewW, viewH);
            Log.d(TAG, "tryAttachSurface(" + caller + "): setVideoSurface + setWindowSize done, calling attachViews()...");
            out.attachViews();
            Log.d(TAG, "tryAttachSurface(" + caller + "): attachViews() returned"
                    + " areViewsAttached=" + out.areViewsAttached());
            try {
                mVlcPlayer.setScale(0f);
                mVlcPlayer.setAspectRatio(null);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            Log.e(TAG, "tryAttachSurface(" + caller + ") FAILED", t);
        }
    }

    void setViewSize(int width, int height) {
        if (width > 1) mViewWidth = width;
        if (height > 1) mViewHeight = height;
    }

    /**
     * decor 首帧尚未测量完成时宽高为 0；布局结束后由 VlcPlayerManager post 调用，补一次 setWindowSize。
     */
    void applyWindowSizeIfAttached(int width, int height) {
        if (mReleased || mVlcPlayer == null || width <= 0 || height <= 0) {
            return;
        }
        mViewWidth = width;
        mViewHeight = height;
        try {
            IVLCVout out = mVlcPlayer.getVLCVout();
            if (!out.areViewsAttached()) {
                return;
            }
            out.setWindowSize(width, height);
            Log.d(TAG, "applyWindowSizeIfAttached: " + width + "x" + height);
            try {
                mVlcPlayer.setScale(0f);
                mVlcPlayer.setAspectRatio(null);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            Log.e(TAG, "applyWindowSizeIfAttached failed", t);
        }
    }

    private void refreshVoutWindowSizeIfAttached() {
        if (mReleased || mVlcPlayer == null) {
            return;
        }
        int w = mViewWidth;
        int h = mViewHeight;
        if (w < 2 || h < 2) {
            android.util.DisplayMetrics dm = mAppContext.getResources().getDisplayMetrics();
            w = dm.widthPixels;
            h = dm.heightPixels;
        }
        try {
            IVLCVout out = mVlcPlayer.getVLCVout();
            if (!out.areViewsAttached()) {
                return;
            }
            out.setWindowSize(w, h);
            Log.d(TAG, "refreshVoutWindowSizeIfAttached: " + w + "x" + h);
            try {
                mVlcPlayer.setScale(0f);
                mVlcPlayer.setAspectRatio(null);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            Log.e(TAG, "refreshVoutWindowSizeIfAttached failed", t);
        }
    }

    // -------------------------------------------------------------------------
    // VLC 事件
    // -------------------------------------------------------------------------

    private void onVlcEventNative(org.videolan.libvlc.MediaPlayer.Event e) {
        mMainHandler.post(() -> onVlcEvent(e));
    }

    @MainThread
    private void onVlcEvent(org.videolan.libvlc.MediaPlayer.Event e) {
        if (mReleased || mVlcPlayer == null) {
            return;
        }
        switch (e.type) {
            case org.videolan.libvlc.MediaPlayer.Event.Opening:
                Log.d(TAG, "VLC event: Opening");
                break;
            case org.videolan.libvlc.MediaPlayer.Event.Buffering:
                mBufferPercent = Math.max(0, Math.min(100, (int) e.getBuffering()));
                Log.d(TAG, "VLC event: Buffering " + mBufferPercent + "%");
                notifyOnBufferingUpdate(mBufferPercent);
                if (!mPreparedSent && mBufferPercent >= 99) {
                    mPreparedSent = true;
                    notifyOnPrepared();
                }
                break;
            case org.videolan.libvlc.MediaPlayer.Event.Playing:
                Log.d(TAG, "VLC event: Playing");
                if (!mPreparedSent) {
                    mPreparedSent = true;
                    notifyOnPrepared();
                }
                break;
            case org.videolan.libvlc.MediaPlayer.Event.Paused:
                Log.d(TAG, "VLC event: Paused");
                break;
            case org.videolan.libvlc.MediaPlayer.Event.Stopped:
                Log.d(TAG, "VLC event: Stopped");
                break;
            case org.videolan.libvlc.MediaPlayer.Event.Vout:
                boolean attached = false;
                try { attached = mVlcPlayer.getVLCVout().areViewsAttached(); } catch (Throwable ignored) {}
                Log.d(TAG, "VLC event: Vout count=" + e.getVoutCount()
                        + " isPlaying=" + mVlcPlayer.isPlaying()
                        + " viewsAttached=" + attached
                        + " surface=" + mSurface
                        + " surfaceValid=" + (mSurface != null && mSurface.isValid()));
                if (e.getVoutCount() > 0) {
                    notifyOnInfo(IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START, 0);
                    updateVideoSizeFromTrack();
                    refreshVoutWindowSizeIfAttached();
                }
                break;
            case org.videolan.libvlc.MediaPlayer.Event.LengthChanged:
                Log.d(TAG, "VLC event: LengthChanged " + e.getLengthChanged() + "ms");
                notifyOnInfo(IMediaPlayer.MEDIA_INFO_METADATA_UPDATE, 0);
                break;
            case org.videolan.libvlc.MediaPlayer.Event.EndReached:
                Log.d(TAG, "VLC event: EndReached");
                notifyOnCompletion();
                break;
            case org.videolan.libvlc.MediaPlayer.Event.EncounteredError:
                Log.e(TAG, "VLC event: EncounteredError  url=" + mDataSource);
                LogUtil.w("LibVLC EncounteredError url=" + mDataSource);
                notifyOnError(IMediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
                break;
            case org.videolan.libvlc.MediaPlayer.Event.TimeChanged:
                if (mSeekPending) {
                    mSeekPending = false;
                    notifyOnSeekComplete();
                }
                break;
            default:
                break;
        }
    }

    private void updateVideoSizeFromTrack() {
        try {
            IMedia.VideoTrack t = mVlcPlayer.getCurrentVideoTrack();
            if (t != null && t.width > 0 && t.height > 0) {
                mVideoW = t.width;
                mVideoH = t.height;
                mSarNum = t.sarNum > 0 ? t.sarNum : 1;
                mSarDen = t.sarDen > 0 ? t.sarDen : 1;
                notifyOnVideoSizeChanged(mVideoW, mVideoH, mSarNum, mSarDen);
            }
        } catch (Throwable ignored) {
        }
    }

    // -------------------------------------------------------------------------
    // Media 绑定（由 VlcPlayerManager.initVideoPlayer 调用）
    // -------------------------------------------------------------------------

    public void bindMediaFromDataSource(Map<String, String> headers) throws IOException {
        if (mReleased) {
            return;
        }
        if ((mDataSource == null || mDataSource.isEmpty()) && mContentFd == null) {
            throw new IOException("empty url");
        }
        Log.d(TAG, "bindMedia url=" + mDataSource);
        Media media;
        if (mContentFd != null) {
            media = new Media(mLibVlc, mContentFd.getFileDescriptor());
        } else {
            media = new Media(mLibVlc, Uri.parse(mDataSource));
        }
        Map<String, String> merged = mergeHeaderMaps(mDataSourceHeadersFromProxy, headers);
        applyHeaders(media, merged);
        if (mDataSource != null && isLikelyNetworkHls(mDataSource)) {
            media.addOption(":network-caching=4000");
            media.addOption(":http-reconnect=true");
            Log.d(TAG, "bindMedia: HLS options applied");
        }
        if (mLooping) {
            media.addOption(":input-repeat=-1");
        }
        mVlcPlayer.setMedia(media);
        media.release();
        mVlcPlayer.setRate(mRate);
        Log.d(TAG, "bindMedia done");
    }

    private static Map<String, String> mergeHeaderMaps(Map<String, String> first, Map<String, String> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return null;
        }
        Map<String, String> out = new HashMap<>();
        if (first != null) out.putAll(first);
        if (second != null) out.putAll(second);
        return out;
    }

    static boolean isLikelyNetworkHls(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.US);
        return u.contains(".m3u8") || u.contains("m3u8?") || u.contains("application/vnd.apple.mpegurl");
    }

    private static void applyHeaders(Media media, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return;
        for (Map.Entry<String, String> en : headers.entrySet()) {
            if (en.getKey() == null || en.getValue() == null) continue;
            String k = en.getKey().trim();
            String v = en.getValue();
            if (k.isEmpty() || v.isEmpty()) continue;
            if (k.equalsIgnoreCase("User-Agent")) {
                media.addOption(":http-user-agent=" + vlcEscape(v));
            } else if (k.equalsIgnoreCase("Referer") || k.equalsIgnoreCase("Referrer")) {
                media.addOption(":http-referrer=" + vlcEscape(v));
            } else {
                media.addOption(":http-header=" + vlcEscape(k + ": " + v));
            }
        }
    }

    private static String vlcEscape(String s) {
        return s.replace("\\", "\\\\").replace(",", "\\,");
    }

    // -------------------------------------------------------------------------
    // IMediaPlayer 核心接口
    // -------------------------------------------------------------------------

    @Override
    public void prepareAsync() throws IllegalStateException {
        if (mReleased) return;
        if (mPlayLaunched) {
            Log.d(TAG, "prepareAsync: already launched, skip");
            return;
        }
        Log.d(TAG, "prepareAsync surface=" + mSurface
                + " valid=" + (mSurface != null && mSurface.isValid()));
        mPlayLaunched = true;
        try {
            if (mSurface != null && mSurface.isValid()) {
                tryAttachSurface("prepareAsync");
            }
            // play() 会触发 VLC 拉流、Buffering、Playing 事件 → onPrepared。
            // 若已 attachViews，VLC 内部 onSurfacesCreated 可能已经调了 play()，
            // 但重复 play() 对 VLC 是安全的（mPlaying=true 时直接 return）。
            // 若无 Surface，play() 直接 nativePlay() 开始无 vout 解码（音频可播）。
            mVlcPlayer.play();
            Log.d(TAG, "prepareAsync: play() called, hasSurface=" + (mSurface != null));
        } catch (Throwable t) {
            Log.e(TAG, "prepareAsync: play() failed", t);
            mPlayLaunched = false;
            notifyOnError(IMediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        }
    }

    @Override
    public void start() throws IllegalStateException {
        if (mVlcPlayer == null || mReleased) return;
        Log.d(TAG, "start() playing=" + mVlcPlayer.isPlaying());
        try {
            if (mSurface != null && mSurface.isValid()) {
                IVLCVout out = mVlcPlayer.getVLCVout();
                if (!out.areViewsAttached()) {
                    tryAttachSurface("start");
                }
            }
            mVlcPlayer.play();
        } catch (Throwable ignored) {
        }
    }

    /**
     * GSY surface 到来时调用（来自 VlcPlayerManager.showDisplay）。
     * <p>
     * 每次都执行 {@link #tryAttachSurface}：内部 detach 旧 Surface → attach 新 Surface，
     * VLC 的 {@code onSurfacesCreated} 回调会自动 {@code setVideoTrackEnabled(true)} 重建 vout。
     */
    void onShowDisplay(Surface surface, int viewW, int viewH) {
        Log.d(TAG, "onShowDisplay surface=" + surface
                + " valid=" + (surface != null && surface.isValid())
                + " viewSize=" + viewW + "x" + viewH
                + " playLaunched=" + mPlayLaunched);
        mSurface = surface;
        if (viewW > 1) mViewWidth = viewW;
        if (viewH > 1) mViewHeight = viewH;
        if (mReleased || mVlcPlayer == null) return;

        if (mSurface != null && mSurface.isValid()) {
            tryAttachSurface("onShowDisplay");
        }
    }

    void onReleaseSurface() {
        boolean wasAttached = false;
        try { wasAttached = mVlcPlayer != null && mVlcPlayer.getVLCVout().areViewsAttached(); } catch (Throwable ignored) {}
        Log.d(TAG, "onReleaseSurface viewsAttached=" + wasAttached
                + " isPlaying=" + (mVlcPlayer != null && !mReleased && mVlcPlayer.isPlaying())
                + " currentSurface=" + mSurface
                + " currentSurfaceValid=" + (mSurface != null && mSurface.isValid()));
        // GSY 的 releaseSurface 走 Handler 消息队列，可能晚于 showDisplay（直接调用）。
        // 若此时已有更新的有效 Surface（由 onShowDisplay 写入），跳过 detach，避免误杀新 Surface。
        if (mSurface != null && mSurface.isValid()) {
            Log.d(TAG, "onReleaseSurface: SKIPPED – newer surface already active");
            return;
        }
        mSurface = null;
        if (mVlcPlayer != null && !mReleased) {
            try {
                IVLCVout out = mVlcPlayer.getVLCVout();
                if (out.areViewsAttached()) {
                    out.detachViews();
                    Log.d(TAG, "onReleaseSurface: detachViews done");
                }
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void stop() throws IllegalStateException {
        if (mVlcPlayer != null && !mReleased) mVlcPlayer.stop();
    }

    @Override
    public void pause() throws IllegalStateException {
        if (mVlcPlayer != null && !mReleased) mVlcPlayer.pause();
    }

    @Override
    public void release() {
        mReleased = true;
        mMainHandler.removeCallbacksAndMessages(null);
        try {
            if (mVlcPlayer != null) {
                IVLCVout out = mVlcPlayer.getVLCVout();
                out.detachViews();
                mVlcPlayer.stop();
                mVlcPlayer.setMedia(null);
                mVlcPlayer.release();
            }
        } catch (Throwable ignored) {
        }
        releaseContentFd();
        mDataSourceHeadersFromProxy = null;
        resetListeners();
    }

    @Override
    public void reset() {
        mPreparedSent = false;
        mPlayLaunched = false;
        mBufferPercent = -1;
        mVideoW = mVideoH = 0;
        mSarNum = mSarDen = 1;
        try {
            if (mVlcPlayer != null) {
                mVlcPlayer.stop();
                mVlcPlayer.setMedia(null);
            }
        } catch (Throwable ignored) {
        }
        releaseContentFd();
        mDataSource = null;
        mDataSourceHeadersFromProxy = null;
        resetListeners();
    }

    // -------------------------------------------------------------------------
    // 其余 IMediaPlayer 实现
    // -------------------------------------------------------------------------

    @Override
    public void setDisplay(SurfaceHolder sh) {
        if (sh != null) setSurface(sh.getSurface());
    }

    @Override
    public void setSurface(Surface surface) {
        mSurface = surface;
    }

    @Override
    public void setDataSource(Context context, Uri uri)
            throws IOException {
        mDataSourceHeadersFromProxy = null;
        setDataSource(uri == null ? null : uri.toString());
    }

    @Override
    public void setDataSource(Context context, Uri uri, Map<String, String> headers)
            throws IOException {
        mDataSourceHeadersFromProxy = (headers != null && !headers.isEmpty())
                ? new HashMap<>(headers) : null;
        setDataSource(uri == null ? null : uri.toString());
    }

    @Override
    public void setDataSource(FileDescriptor fd) throws IOException {
        throw new IOException("FileDescriptor not supported for LibVLC");
    }

    @Override
    public void setDataSource(String path) throws IOException {
        releaseContentFd();
        mDataSourceHeadersFromProxy = null;
        mDataSource = path;
    }

    public void setDataSourceFromContent(Context context, Uri uri) throws IOException {
        releaseContentFd();
        mDataSourceHeadersFromProxy = null;
        mDataSource = null;
        mContentFd = context.getContentResolver().openFileDescriptor(uri, "r");
        if (mContentFd == null) throw new IOException("openFileDescriptor failed");
    }

    @Override
    public String getDataSource() {
        return mDataSource;
    }

    private void releaseContentFd() {
        if (mContentFd != null) {
            try { mContentFd.close(); } catch (IOException ignored) {}
            mContentFd = null;
        }
    }

    public void setSpeed(float speed, int unused) {
        mRate = speed > 0 ? speed : 1f;
        if (mVlcPlayer != null && !mReleased) {
            try { mVlcPlayer.setRate(mRate); } catch (Throwable ignored) {}
        }
    }

    @Override
    public void seekTo(long msec) throws IllegalStateException {
        if (mVlcPlayer != null && !mReleased) {
            mSeekPending = true;
            mVlcPlayer.setTime(msec);
        }
    }

    @Override
    public long getCurrentPosition() {
        if (mVlcPlayer == null || mReleased) return 0;
        try { return mVlcPlayer.getTime(); } catch (Throwable t) { return 0; }
    }

    @Override
    public long getDuration() {
        if (mVlcPlayer == null || mReleased) return 0;
        try {
            long len = mVlcPlayer.getLength();
            return len > 0 ? len : 0;
        } catch (Throwable t) { return 0; }
    }

    @Override public int getVideoWidth() { return mVideoW; }
    @Override public int getVideoHeight() { return mVideoH; }
    @Override public int getVideoSarNum() { return mSarNum; }
    @Override public int getVideoSarDen() { return mSarDen; }
    @Override public boolean isPlaying() { return mVlcPlayer != null && !mReleased && mVlcPlayer.isPlaying(); }
    @Override public int getAudioSessionId() { return 0; }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mVlcPlayer == null || mReleased) return;
        int v = (int) (Math.max(0f, Math.min(1f, Math.max(leftVolume, rightVolume))) * 100);
        try { mVlcPlayer.setVolume(v); } catch (Throwable ignored) {}
    }

    @Override
    public MediaInfo getMediaInfo() {
        MediaInfo mi = new MediaInfo();
        mi.mMediaPlayerName = "libvlc";
        return mi;
    }

    @Override public void setLogEnabled(boolean enable) {}
    @Override public boolean isPlayable() { return true; }
    @Override public void setAudioStreamType(int streamtype) {}
    @Override public void setKeepInBackground(boolean keepInBackground) {}
    @Override public void setWakeMode(Context context, int mode) {}
    @Override public void setLooping(boolean looping) { mLooping = looping; }
    @Override public boolean isLooping() { return mLooping; }
    @Override public void setScreenOnWhilePlaying(boolean screenOn) {}
    @Override public ITrackInfo[] getTrackInfo() { return new ITrackInfo[0]; }
    @Override public void setDataSource(IMediaDataSource mediaDataSource) { throw new UnsupportedOperationException(); }
}
