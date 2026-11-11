package com.openapi.ks.myapp.ui.gsy;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.openapi.common.util.LogUtil;
import com.openapi.core.view.view.MyFavoriteView;

import com.openapi.feature.video.R;
import com.openapi.ks.myapp.data.ImagePhotoLoad;
import com.openapi.ks.myapp.ui.sound.VideoPlaybackUiHelper;

import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.utils.CommonUtil;
import com.shuyu.gsyvideoplayer.utils.OrientationUtils;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;
import com.shuyu.gsyvideoplayer.video.base.GSYVideoView;

import java.io.File;
import java.util.Map;

/**
 * 详情页用 GSY 标准播放器，叠加收藏条与懒加载遮罩（替代原 MyJzvdStd）。
 * 进度条拖动、「更多」菜单、自动换内核、投屏按钮、刘海避让已拆到同包的小类里。
 */
public class DetailGSYVideoPlayer extends StandardGSYVideoPlayer {

    /** 横向滑动松手后快进/快退的固定步长（毫秒） */
    private static final long SWIPE_SEEK_STEP_MS = 30_000L;

    private MyFavoriteView myFavoriteView;
    private FrameLayout loadingOverlay;
    private String mLastSetupUrl;

    private final CastButtonBinder castButton = new CastButtonBinder();
    private final EngineFallback engineFallback = new EngineFallback();
    private final SeekProgressController seekProgress = new SeekProgressController(new SeekProgressController.Host() {
        @Override
        public long duration() {
            return getDuration();
        }

        @Override
        public long currentPositionWhenPlaying() {
            return getCurrentPositionWhenPlaying();
        }

        @Override
        public SeekBar progressBar() {
            return mProgressBar;
        }

        @Override
        public ProgressBar bottomProgressBar() {
            return mBottomProgressBar;
        }

        @Override
        public TextView currentTimeView() {
            return mCurrentTimeTextView;
        }

        @Override
        public TextView totalTimeView() {
            return mTotalTimeTextView;
        }
    });

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
        castButton.install(this, activity);
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
        PlayerInsets.apply(this);
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
            PlayerMoreMenu.show(this);
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

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final int pid = v.getId();
        final int action = event.getActionMasked();
        if (pid == com.shuyu.gsyvideoplayer.R.id.progress && mProgressBar != null) {
            if (action == MotionEvent.ACTION_DOWN) {
                seekProgress.onSeekBarPressed(mProgressBar.getProgress());
            } else if (action == MotionEvent.ACTION_MOVE) {
                // 竖屏部分机型 SeekBar 拖动时 onProgressChanged 的 fromUser 恒为 false，
                // 仅靠 post 一帧可能错过与父布局的拦截顺序；MOVE 上直接按当前 thumb 刷新时间。
                seekProgress.applyProgress(mProgressBar.getProgress());
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                seekProgress.onSeekBarReleased();
            }
        }
        boolean r = super.onTouch(v, event);
        // 在 GSY 处理完同一帧触摸后再读 progress，避免 thumb 未更新
        if (pid == com.shuyu.gsyvideoplayer.R.id.progress && mProgressBar != null
                && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE)) {
            seekProgress.scheduleUiTick();
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
            engineFallback.resetForNewUrl();
            mLastSetupUrl = url;
            seekProgress.resetForNewUrl();
        }
        return super.setUp(url, cacheWithPlay, cachePath, mapHeadData, title);
    }

    @Override
    protected void setProgressAndTime(long progress, long secProgress, long currentTime, long totalTime, boolean forceChange) {
        seekProgress.onTotalTimeKnown(totalTime);
        super.setProgressAndTime(progress, secProgress, currentTime, totalTime, forceChange);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        super.onStartTrackingTouch(seekBar);
        if (seekBar == mProgressBar) {
            seekProgress.refreshCachedDuration();
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (seekBar == mProgressBar) {
            seekProgress.applyProgress(seekBar.getProgress());
        }
        super.onStopTrackingTouch(seekBar);
    }

    /**
     * 拖动进度条时更新当前时间：部分机型 {@code fromUser} 恒为 false；且首帧可能早于 {@code mHadSeekTouch}，
     * 故结合拖动状态与 {@link #onTouch} 里的 post 刷新。
     */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        super.onProgressChanged(seekBar, progress, fromUser);
        if (seekBar == mProgressBar && isShowDragProgressTextOnSeekBar()
                && (mHadSeekTouch || seekProgress.isDragging() || seekBar.isPressed())) {
            seekProgress.applyProgress(progress);
        }
    }

    @Override
    protected void showDragProgressTextOnSeekBar(boolean fromUser, int progress) {
        if (!isShowDragProgressTextOnSeekBar()) {
            return;
        }
        boolean dragging = seekProgress.isDragging() || mHadSeekTouch
                || (mProgressBar != null && mProgressBar.isPressed());
        if (!fromUser && !dragging) {
            return;
        }
        seekProgress.applyProgress(progress);
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
        seekProgress.cancelUiTick();
        engineFallback.restoreIfTemporary(getContext());
        super.onDetachedFromWindow();
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
        if (engineFallback.tryAlternate(this, mOriginUrl, this::retryAfterEngineSwitch)) {
            return;
        }
        VideoPlaybackUiHelper.onGsyDetailPlaybackError(this, what, extra);
    }

    /** 换内核后重新准备并播放当前片源。 */
    private void retryAfterEngineSwitch() {
        release();
        setUp(mOriginUrl, mCache, null, mMapHeadData, mTitle);
        startPlayLogic();
    }
}
