package com.openapi.ks.myapp.ui.gsy;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.mediarouter.app.MediaRouteButton;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.text.TextUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.openai.commonutil.LogUtil;
import com.openai.commonutil.ToastUtil;
import com.openai.commonview.view.MyFavoriteView;

import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.cast.CastFrameworkUtil;
import com.openapi.ks.myapp.data.ImagePhotoLoad;
import com.openapi.ks.myapp.ui.sound.VideoPlaybackUiHelper;
import com.openapi.ks.myapp.ui.user.SettingActivity;
import com.openapi.ks.myapp.video.PlaybackEngineHelper;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.utils.CommonUtil;
import com.shuyu.gsyvideoplayer.utils.OrientationUtils;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;
import com.shuyu.gsyvideoplayer.video.base.GSYVideoView;

import java.io.File;
import java.util.Map;

/**
 * 详情页用 GSY 标准播放器，叠加收藏条与懒加载遮罩（替代原 MyJzvdStd）。
 */
public class DetailGSYVideoPlayer extends StandardGSYVideoPlayer {

    /** 横向滑动松手后快进/快退的固定步长（毫秒） */
    private static final long SWIPE_SEEK_STEP_MS = 30_000L;

    private MyFavoriteView myFavoriteView;
    /** Google Cast 设备选择按钮，由 {@link #installGoogleCastRouteButton(AppCompatActivity)} 注入 */
    private MediaRouteButton mCastRouteButton;
    private FrameLayout loadingOverlay;
    private String mLastSetupUrl;
    private boolean mEngineFallbackTried;
    /** 自动切换内核重试前保存的用户偏好，detach 时恢复 */
    private int mRestoreEngineAfterDetach = Integer.MIN_VALUE;
    /** 部分片源 {@link #getDuration()} 在拖动瞬间为 0，用进度回调里的总长兜底，保证进度条时间实时更新 */
    private long mCachedDurationMs;
    /**
     * 手指按下主 SeekBar 时置 true（早于 {@link #onStartTrackingTouch}），避免首帧 onProgressChanged 时 mHadSeekTouch 仍为 false。
     */
    private boolean mDraggingStandardSeekBar;
    /** 按下时进度与播放位置，用于 getDuration 为 0 时反推总时长 */
    private int mSeekAnchorProgress = -1;
    private long mSeekAnchorPositionMs = -1;
    private final Runnable mSeekBarUiTick = new Runnable() {
        @Override
        public void run() {
            if (mProgressBar != null) {
                applySeekBarProgressToTimeViews(mProgressBar.getProgress());
            }
        }
    };

    public DetailGSYVideoPlayer(Context context) {
        super(context);
    }

    /**
     * GSY 全屏窗口通过反射优先使用该构造；缺失时可能回退到仅 Context，但补上更稳妥。
     */
    public DetailGSYVideoPlayer(Context context, Boolean fullFlag) {
        super(context, fullFlag);
    }

    public DetailGSYVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** 供 Activity {@code onConfigurationChanged} 转发，避免横屏/全屏按钮与旋转异常 */
    public OrientationUtils getOrientationUtilsForActivity() {
        return mOrientationUtils;
    }

    /**
     * 在播放器右上角显示 Google Cast 投屏按钮（需系统已安装 Google Play 服务）。
     * 与 {@link com.openapi.ks.myapp.cast.GoogleCastSessionController} 配合使用。
     */
    public void installGoogleCastRouteButton(AppCompatActivity activity) {
        if (mCastRouteButton != null || activity == null) {
            return;
        }
        if (CastFrameworkUtil.tryGetCastContext(activity) == null) {
            LogUtil.i("Google Cast unavailable (no Cast dynamite / GMS)");
            return;
        }
        mCastRouteButton = new MediaRouteButton(activity);
        try {
            CastButtonFactory.setUpMediaRouteButton(activity, mCastRouteButton);
        } catch (Throwable t) {
            LogUtil.i("CastButtonFactory.setUpMediaRouteButton failed: " + t.getMessage());
            mCastRouteButton = null;
            return;
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        int m12 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
                getResources().getDisplayMetrics());
        int m48 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48,
                getResources().getDisplayMetrics());
        lp.setMargins(0, m48, m12, 0);
        addView(mCastRouteButton, lp);
    }

    @Override
    protected void init(Context context) {
        super.init(context);
        setStartAfterPrepared(false);
        setShowDragProgressTextOnSeekBar(true);
        myFavoriteView = new MyFavoriteView(context);
        myFavoriteView.setId(R.id.my_favoriteview);
        FrameLayout.LayoutParams favLp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        favLp.gravity = Gravity.END | Gravity.BOTTOM;
        int m22 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22,
                getResources().getDisplayMetrics());
        int m55 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 55,
                getResources().getDisplayMetrics());
        favLp.setMargins(0, 0, m22, m55);
        addView(myFavoriteView, favLp);

        loadingOverlay = new FrameLayout(context);
        loadingOverlay.setId(R.id.loading_init);
        FrameLayout.LayoutParams loadLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        ProgressBar pb = new ProgressBar(context);
        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        pbLp.gravity = Gravity.CENTER;
        loadingOverlay.addView(pb, pbLp);
        loadingOverlay.setVisibility(View.GONE);
        addView(loadingOverlay, loadLp);

        wireMoreButton();
        applyPlayerChromeInsets();
    }

    @Override
    public int getLayoutId() {
        return R.layout.video_layout_detail_modern;
    }

    private void wireMoreButton() {
        View more = findViewById(R.id.detail_player_more);
        if (more != null) {
            more.setOnClickListener(this);
        }
    }

    private void applyPlayerChromeInsets() {
        View top = findViewById(R.id.layout_top);
        if (top != null) {
            ViewCompat.setOnApplyWindowInsetsListener(top, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), sys.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }
        View bottom = findViewById(R.id.layout_bottom);
        if (bottom != null) {
            final int baseNavPad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4,
                    getResources().getDisplayMetrics());
            ViewCompat.setOnApplyWindowInsetsListener(bottom, (v, insets) -> {
                Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                        baseNavPad + nav.bottom);
                return insets;
            });
        }
        ViewCompat.requestApplyInsets(this);
    }

    /**
     * GSY 12.x 的 {@link com.shuyu.gsyvideoplayer.video.base.GSYVideoControlView#onClick} 未处理 {@code R.id.fullscreen}，
     * 官方详情页是通过 {@code GSYBaseActivityDetail#initVideo} 给全屏按钮单独绑监听。普通 Activity + 布局内播放器时必须自行处理。
     * <p>
     * 同理补充 {@code R.id.back}：非 GSYBaseActivityDetail 场景下顶部返回键需自行退出全屏 / 交给 Activity 处理，
     * 否则点击无响应（与解码内核无关）。
     */
    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.detail_player_more) {
            showPlayerMoreMenu();
            return;
        }
        if (id == R.id.back || id == com.shuyu.gsyvideoplayer.R.id.back) {
            onBackButtonClicked();
            return;
        }
        if (id == com.shuyu.gsyvideoplayer.R.id.fullscreen) {
            onFullscreenButtonClicked();
            return;
        }
        super.onClick(v);
    }

    /**
     * 顶部返回：先退出 GSY 窗口全屏，再与系统返回键一致（{@link GSYVideoManager#backFromWindowFull}）。
     */
    private void onBackButtonClicked() {
        Activity act = CommonUtil.scanForActivity(getContext());
        if (act == null) {
            return;
        }
        if (mIfCurrentIsFullscreen) {
            mFullAnimEnd = true;
            clearFullscreenLayout();
            return;
        }
        if (act instanceof AppCompatActivity) {
            AppCompatActivity app = (AppCompatActivity) act;
            if (GSYVideoManager.backFromWindowFull(app)) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.getOnBackPressedDispatcher().onBackPressed();
            } else {
                app.onBackPressed();
            }
            return;
        }
        act.finish();
    }

    /**
     * GSY {@link com.shuyu.gsyvideoplayer.video.base.GSYBaseVideoPlayer#clearFullscreenLayout} 在
     * {@code mFullAnimEnd == false} 时直接 return，全屏进入动画未完成或异常时会导致「退出全屏」永久失效。
     */
    @Override
    protected void clearFullscreenLayout() {
        mFullAnimEnd = true;
        super.clearFullscreenLayout();
    }

    private void showPlayerMoreMenu() {
        Activity act = CommonUtil.scanForActivity(getContext());
        if (!(act instanceof AppCompatActivity)) {
            return;
        }
        AppCompatActivity appAct = (AppCompatActivity) act;
        BottomSheetDialog dlg = new BottomSheetDialog(appAct);
        View sheet = LayoutInflater.from(appAct).inflate(R.layout.sheet_player_more, null);
        dlg.setContentView(sheet);
        LinearLayout speedBox = sheet.findViewById(R.id.player_speed_container);
        if (speedBox == null) {
            dlg.dismiss();
            return;
        }
        int padV = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8,
                getResources().getDisplayMetrics());
        int padH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                getResources().getDisplayMetrics());
        TypedValue selectable = new TypedValue();
        appAct.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true);
        final float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        for (float sp : speeds) {
            TextView row = new TextView(appAct);
            row.setText(formatSpeedLabel(sp));
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            row.setTextColor(0xFF1A1A1A);
            row.setPadding(padH, padV, padH, padV);
            if (selectable.resourceId != 0) {
                row.setBackgroundResource(selectable.resourceId);
            }
            row.setOnClickListener(v -> {
                try {
                    DetailGSYVideoPlayer.this.setSpeed(sp, true);
                } catch (Throwable t) {
                    LogUtil.e(t);
                }
                dlg.dismiss();
                ToastUtil.showShort(getContext().getString(R.string.player_speed_applied, formatSpeedLabel(sp)));
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = padV / 2;
            speedBox.addView(row, lp);
        }
        TextView decoderRow = new TextView(appAct);
        decoderRow.setText(R.string.player_more_decoder_settings);
        decoderRow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        decoderRow.setTextColor(0xFF1A1A1A);
        decoderRow.setPadding(padH, padV, padH, padV);
        if (selectable.resourceId != 0) {
            decoderRow.setBackgroundResource(selectable.resourceId);
        }
        decoderRow.setOnClickListener(v -> {
            dlg.dismiss();
            getContext().startActivity(new Intent(getContext(), SettingActivity.class));
        });
        speedBox.addView(decoderRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dlg.show();
    }

    /** 供流式字幕解音频：与 GSY 当前播放地址一致（含 urlToken 等）。 */
    public String getPlaybackUrl() {
        if (mOriginUrl != null && !mOriginUrl.isEmpty()) {
            return mOriginUrl;
        }
        return mUrl;
    }

    /** 与 {@link #setUp} 传入的 Referer / User-Agent 等一致。 */
    public Map<String, String> getPlaybackHeaders() {
        return mMapHeadData;
    }

    private static String formatSpeedLabel(float s) {
        if (Math.abs(s - 0.5f) < 0.01f) {
            return "0.5×";
        }
        if (Math.abs(s - 0.75f) < 0.01f) {
            return "0.75×";
        }
        if (Math.abs(s - 1f) < 0.01f) {
            return "1×";
        }
        if (Math.abs(s - 1.25f) < 0.01f) {
            return "1.25×";
        }
        if (Math.abs(s - 1.5f) < 0.01f) {
            return "1.5×";
        }
        if (Math.abs(s - 2f) < 0.01f) {
            return "2×";
        }
        return s + "×";
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final int pid = v.getId();
        final int action = event.getActionMasked();
        if (pid == com.shuyu.gsyvideoplayer.R.id.progress && mProgressBar != null) {
            if (action == MotionEvent.ACTION_DOWN) {
                mDraggingStandardSeekBar = true;
                mSeekAnchorProgress = mProgressBar.getProgress();
                mSeekAnchorPositionMs = getCurrentPositionWhenPlaying();
                refreshCachedDurationForSeek();
            } else if (action == MotionEvent.ACTION_MOVE) {
                // 竖屏部分机型 SeekBar 拖动时 onProgressChanged 的 fromUser 恒为 false，
                // 仅靠 post 一帧可能错过与父布局的拦截顺序；MOVE 上直接按当前 thumb 刷新时间。
                applySeekBarProgressToTimeViews(mProgressBar.getProgress());
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mDraggingStandardSeekBar = false;
            }
        }
        boolean r = super.onTouch(v, event);
        // 在 GSY 处理完同一帧触摸后再读 progress，避免 thumb 未更新
        if (pid == com.shuyu.gsyvideoplayer.R.id.progress && mProgressBar != null
                && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE)) {
            mProgressBar.removeCallbacks(mSeekBarUiTick);
            mProgressBar.post(mSeekBarUiTick);
        }
        return r;
    }

    private void onFullscreenButtonClicked() {
        if (mIfCurrentIsFullscreen) {
            clearFullscreenLayout();
            return;
        }
        Activity act = CommonUtil.scanForActivity(getContext());
        if (act == null) {
            return;
        }
        if (mNeedOrientationUtils) {
            if (mOrientationUtils == null) {
                mOrientationUtils = new OrientationUtils(act, this, getOrientationOption());
                mOrientationUtils.setEnable(false);
                mOrientationUtils.setRotateWithSystem(isRotateWithSystem());
                mOrientationUtils.setOnlyRotateLand(isOnlyRotateLand());
            }
            if (mOrientationUtils.getIsLand() != 1) {
                mOrientationUtils.resolveByClick();
            }
        }
        startWindowFullscreen(act, isFullHideActionBar(), isFullHideStatusBar());
    }

    @Override
    public boolean setUp(String url, boolean cacheWithPlay, File cachePath, Map<String, String> mapHeadData, String title) {
        if (url != null && !url.equals(mLastSetupUrl)) {
            mEngineFallbackTried = false;
            mLastSetupUrl = url;
            mCachedDurationMs = 0;
        }
        boolean ok = super.setUp(url, cacheWithPlay, cachePath, mapHeadData, title);
        return ok;
    }

    @Override
    protected void setProgressAndTime(long progress, long secProgress, long currentTime, long totalTime, boolean forceChange) {
        if (totalTime > 0) {
            mCachedDurationMs = totalTime;
        }
        super.setProgressAndTime(progress, secProgress, currentTime, totalTime, forceChange);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        super.onStartTrackingTouch(seekBar);
        if (seekBar == mProgressBar) {
            refreshCachedDurationForSeek();
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (seekBar == mProgressBar) {
            applySeekBarProgressToTimeViews(seekBar.getProgress());
        }
        super.onStopTrackingTouch(seekBar);
    }

    /**
     * 拖动进度条时更新当前时间：部分机型 {@code fromUser} 恒为 false；且首帧可能早于 {@code mHadSeekTouch}，
     * 故结合 {@link #mDraggingStandardSeekBar} 与 {@link #onTouch} 里的 post 刷新。
     */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        super.onProgressChanged(seekBar, progress, fromUser);
        if (seekBar == mProgressBar && isShowDragProgressTextOnSeekBar()
                && (mHadSeekTouch || mDraggingStandardSeekBar || seekBar.isPressed())) {
            applySeekBarProgressToTimeViews(progress);
        }
    }

    @Override
    protected void showDragProgressTextOnSeekBar(boolean fromUser, int progress) {
        if (!isShowDragProgressTextOnSeekBar()) {
            return;
        }
        boolean dragging = mDraggingStandardSeekBar || mHadSeekTouch
                || (mProgressBar != null && mProgressBar.isPressed());
        if (!fromUser && !dragging) {
            return;
        }
        applySeekBarProgressToTimeViews(progress);
    }

    private void refreshCachedDurationForSeek() {
        long d = getDuration();
        if (d > 0) {
            mCachedDurationMs = d;
            return;
        }
        if (mCachedDurationMs > 0) {
            return;
        }
        if (mTotalTimeTextView != null) {
            long p = parseTimeLabelToMs(mTotalTimeTextView.getText());
            if (p > 0) {
                mCachedDurationMs = p;
            }
        }
    }

    private void applySeekBarProgressToTimeViews(int progress) {
        if (mCurrentTimeTextView == null) {
            return;
        }
        long duration = resolveDurationMsForSeekUi();
        if (duration <= 0) {
            return;
        }
        SeekBar sb = mProgressBar;
        int max = (sb != null && sb.getMax() > 0) ? sb.getMax() : 100;
        long timeMs = max > 0 ? progress * duration / max : 0;
        mCurrentTimeTextView.setText(CommonUtil.stringForTime(timeMs));
        mCurrentTimeTextView.setVisibility(View.VISIBLE);
        if (mBottomProgressBar != null && max > 0) {
            int bottomProg = (int) (progress * 100L / max);
            mBottomProgressBar.setProgress(bottomProg);
        }
    }

    private long resolveDurationMsForSeekUi() {
        long duration = getDuration();
        if (duration > 0) {
            mCachedDurationMs = duration;
            return duration;
        }
        if (mCachedDurationMs > 0) {
            return mCachedDurationMs;
        }
        if (mTotalTimeTextView != null) {
            long parsed = parseTimeLabelToMs(mTotalTimeTextView.getText());
            if (parsed > 0) {
                mCachedDurationMs = parsed;
                return parsed;
            }
        }
        int max = mProgressBar != null && mProgressBar.getMax() > 0 ? mProgressBar.getMax() : 100;
        if (mSeekAnchorProgress > 0 && mSeekAnchorPositionMs >= 0) {
            long inferred = mSeekAnchorPositionMs * max / mSeekAnchorProgress;
            if (inferred > 0) {
                mCachedDurationMs = inferred;
                return inferred;
            }
        }
        SeekBar sb = mProgressBar;
        if (sb != null) {
            int prog = sb.getProgress();
            long pos = getCurrentPositionWhenPlaying();
            if (prog > 0 && pos >= 0) {
                long inferred = pos * max / prog;
                if (inferred > 0) {
                    mCachedDurationMs = inferred;
                    return inferred;
                }
            }
        }
        return 0;
    }

    /** 与 GSY {@link CommonUtil#stringForTime} 常见输出一致：mm:ss 或 H:mm:ss */
    private static long parseTimeLabelToMs(CharSequence text) {
        if (text == null || TextUtils.isEmpty(text)) {
            return 0;
        }
        String t = text.toString().trim();
        String[] parts = t.split(":");
        try {
            if (parts.length == 2) {
                long m = Long.parseLong(parts[0]);
                long s = Long.parseLong(parts[1]);
                return (m * 60 + s) * 1000;
            }
            if (parts.length == 3) {
                long h = Long.parseLong(parts[0]);
                long m = Long.parseLong(parts[1]);
                long s = Long.parseLong(parts[2]);
                return (h * 3600 + m * 60 + s) * 1000;
            }
        } catch (NumberFormatException ignored) {
        }
        return 0;
    }

    @Override
    protected void touchSurfaceMove(float deltaX, float deltaY, float y) {
        if (mChangePosition) {
            long dur = getDuration();
            long down = mDownPosition;
            int cmp = Float.compare(deltaX, 0f);
            if (cmp > 0) {
                mSeekTimePosition = dur > 0 ? Math.min(dur, down + SWIPE_SEEK_STEP_MS) : down + SWIPE_SEEK_STEP_MS;
            } else if (cmp < 0) {
                mSeekTimePosition = Math.max(0, down - SWIPE_SEEK_STEP_MS);
            } else {
                mSeekTimePosition = down;
            }
            // 底部栏「当前时间 + 进度条」与滑动目标同步（用户反馈仅弹层不够）
            if (mCurrentTimeTextView != null) {
                mCurrentTimeTextView.setText(CommonUtil.stringForTime(mSeekTimePosition));
            }
            if (dur > 0) {
                int pct = (int) (mSeekTimePosition * 100 / dur);
                if (mProgressBar != null) {
                    mProgressBar.setProgress(pct);
                }
                if (mBottomProgressBar != null) {
                    mBottomProgressBar.setProgress(pct);
                }
            }
            String seekTime = CommonUtil.stringForTime(mSeekTimePosition);
            String totalTime = CommonUtil.stringForTime(dur);
            showProgressDialog(deltaX, seekTime, mSeekTimePosition, totalTime, dur);
            return;
        }
        super.touchSurfaceMove(deltaX, deltaY, y);
    }

    @Override
    protected void touchSurfaceUp() {
        if (mChangePosition) {
            long dur = getDuration();
            long down = mDownPosition;
            int cmp = Long.compare(mSeekTimePosition, down);
            if (cmp > 0) {
                mSeekTimePosition = dur > 0 ? Math.min(dur, down + SWIPE_SEEK_STEP_MS) : down + SWIPE_SEEK_STEP_MS;
            } else if (cmp < 0) {
                mSeekTimePosition = Math.max(0, down - SWIPE_SEEK_STEP_MS);
            } else {
                mSeekTimePosition = down;
            }
        }
        super.touchSurfaceUp();
    }

    public MyFavoriteView getMyFavoriteView() {
        return myFavoriteView;
    }

    public void loadingInit() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
    }

    public void closeLoadingInit() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mProgressBar != null) {
            mProgressBar.removeCallbacks(mSeekBarUiTick);
        }
        restorePlaybackEngineIfTemporaryFallback();
        super.onDetachedFromWindow();
    }

    private void restorePlaybackEngineIfTemporaryFallback() {
        if (mRestoreEngineAfterDetach == Integer.MIN_VALUE) {
            return;
        }
        int saved = mRestoreEngineAfterDetach;
        mRestoreEngineAfterDetach = Integer.MIN_VALUE;
        try {
            Context app = getContext().getApplicationContext();
            PlaybackEngineHelper.applyEngineByIndex(app, saved);
        } catch (Throwable t) {
            LogUtil.e(t);
        }
    }

    /** 封面图加载到 GSY 标准布局的 thumbImage。 */
    public void loadPoster(Context context, String imgUrl) {
        int tid = getResources().getIdentifier("thumbImage", "id", "com.shuyu.gsyvideoplayer");
        if (tid == 0) {
            return;
        }
        ImageView iv = findViewById(tid);
        if (iv != null) {
            iv.setScaleType(ImageView.ScaleType.CENTER);
            ImagePhotoLoad.loadCommonImg(context, imgUrl, iv);
        }
    }

    @Override
    public void onError(int what, int extra) {
        super.onError(what, extra);
        if (what == 38 || what == -38) {
            return;
        }
        if (getCurrentState() != GSYVideoView.CURRENT_STATE_ERROR) {
            return;
        }
        if (tryFallbackAlternateEngine()) {
            return;
        }
        VideoPlaybackUiHelper.onGsyDetailPlaybackError(this, what, extra);
    }

    /**
     * 当前片源失败时自动换内核重试一次（不写设置项）：Exo ↔ IJK；阿里 / 系统 失败时先临时换 Exo。
     */
    private boolean tryFallbackAlternateEngine() {
        if (mEngineFallbackTried || mOriginUrl == null || mOriginUrl.isEmpty()) {
            return false;
        }
        Context app = getContext().getApplicationContext();
        int cur = PlaybackEngineHelper.getEngine(app);
        int alt = PlaybackEngineHelper.alternateEngineForPlaybackRetry(cur);
        if (alt == cur) {
            return false;
        }
        mEngineFallbackTried = true;
        mRestoreEngineAfterDetach = cur;
        String from = PlaybackEngineHelper.engineLabel(app, cur);
        String to = PlaybackEngineHelper.engineLabel(app, alt);
        LogUtil.i("GSY playback engine fallback: " + from + " -> " + to + " url=" + mOriginUrl);
        ToastUtil.showShort(getContext().getString(R.string.video_playback_retry_other_engine, from, to));
        PlaybackEngineHelper.applyEngineByIndex(app, alt);
        post(() -> {
            try {
                release();
                setUp(mOriginUrl, mCache, null, mMapHeadData, mTitle);
                startPlayLogic();
            } catch (Throwable t) {
                LogUtil.e(t);
            }
        });
        return true;
    }
}
