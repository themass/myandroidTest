package com.openapi.ks.myapp.video.player;

import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.shuyu.gsyvideoplayer.utils.CommonUtil;

/**
 * 详情页播放器的进度条拖动与时间显示（从 {@link DetailGSYVideoPlayer} 拆出，行为不变）。
 * 部分片源 getDuration 在拖动瞬间为 0，这里用缓存的总时长、时间标签或按播放位置反推兜底。
 */
final class SeekProgressController {

    /** 播放器提供的数据和视图 */
    interface Host {
        long duration();

        long currentPositionWhenPlaying();

        SeekBar progressBar();

        ProgressBar bottomProgressBar();

        TextView currentTimeView();

        TextView totalTimeView();
    }

    private final Host host;
    /** 部分片源 getDuration 在拖动瞬间为 0，用进度回调里的总长兜底 */
    private long cachedDurationMs;
    /** 手指按下主 SeekBar 时置 true，早于 onStartTrackingTouch */
    private boolean dragging;
    /** 按下时进度与播放位置，用于 getDuration 为 0 时反推总时长 */
    private int seekAnchorProgress = -1;
    private long seekAnchorPositionMs = -1;

    final Runnable uiTick = new Runnable() {
        @Override
        public void run() {
            SeekBar sb = host.progressBar();
            if (sb != null) {
                applyProgress(sb.getProgress());
            }
        }
    };

    SeekProgressController(Host host) {
        this.host = host;
    }

    void resetForNewUrl() {
        cachedDurationMs = 0;
    }

    void onTotalTimeKnown(long totalTimeMs) {
        if (totalTimeMs > 0) {
            cachedDurationMs = totalTimeMs;
        }
    }

    boolean isDragging() {
        return dragging;
    }

    void onSeekBarPressed(int progress) {
        dragging = true;
        seekAnchorProgress = progress;
        seekAnchorPositionMs = host.currentPositionWhenPlaying();
        refreshCachedDuration();
    }

    void onSeekBarReleased() {
        dragging = false;
    }

    void scheduleUiTick() {
        SeekBar sb = host.progressBar();
        if (sb != null) {
            sb.removeCallbacks(uiTick);
            sb.post(uiTick);
        }
    }

    void cancelUiTick() {
        SeekBar sb = host.progressBar();
        if (sb != null) {
            sb.removeCallbacks(uiTick);
        }
    }

    void refreshCachedDuration() {
        long d = host.duration();
        if (d > 0) {
            cachedDurationMs = d;
            return;
        }
        if (cachedDurationMs > 0) {
            return;
        }
        TextView total = host.totalTimeView();
        if (total != null) {
            long p = parseTimeLabelToMs(total.getText());
            if (p > 0) {
                cachedDurationMs = p;
            }
        }
    }

    /** 按进度条的 progress 刷新当前时间文字和底部细进度条。 */
    void applyProgress(int progress) {
        TextView current = host.currentTimeView();
        if (current == null) {
            return;
        }
        long duration = resolveDuration();
        if (duration <= 0) {
            return;
        }
        SeekBar sb = host.progressBar();
        int max = (sb != null && sb.getMax() > 0) ? sb.getMax() : 100;
        long timeMs = max > 0 ? progress * duration / max : 0;
        current.setText(CommonUtil.stringForTime(timeMs));
        current.setVisibility(View.VISIBLE);
        ProgressBar bottom = host.bottomProgressBar();
        if (bottom != null && max > 0) {
            bottom.setProgress((int) (progress * 100L / max));
        }
    }

    private long resolveDuration() {
        long duration = host.duration();
        if (duration > 0) {
            cachedDurationMs = duration;
            return duration;
        }
        if (cachedDurationMs > 0) {
            return cachedDurationMs;
        }
        TextView total = host.totalTimeView();
        if (total != null) {
            long parsed = parseTimeLabelToMs(total.getText());
            if (parsed > 0) {
                cachedDurationMs = parsed;
                return parsed;
            }
        }
        SeekBar sb = host.progressBar();
        int max = sb != null && sb.getMax() > 0 ? sb.getMax() : 100;
        if (seekAnchorProgress > 0 && seekAnchorPositionMs >= 0) {
            long inferred = seekAnchorPositionMs * max / seekAnchorProgress;
            if (inferred > 0) {
                cachedDurationMs = inferred;
                return inferred;
            }
        }
        if (sb != null) {
            int prog = sb.getProgress();
            long pos = host.currentPositionWhenPlaying();
            if (prog > 0 && pos >= 0) {
                long inferred = pos * max / prog;
                if (inferred > 0) {
                    cachedDurationMs = inferred;
                    return inferred;
                }
            }
        }
        return 0;
    }

    /** 与 GSY {@link CommonUtil#stringForTime} 常见输出一致：mm:ss 或 H:mm:ss */
    static long parseTimeLabelToMs(CharSequence text) {
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
}
