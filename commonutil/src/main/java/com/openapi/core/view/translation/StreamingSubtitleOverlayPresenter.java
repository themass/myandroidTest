package com.openapi.core.view.translation;

import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.core.util.R;
import com.openapi.core.util.ToastUtil;
import com.openapi.core.view.view.widget.DraggableTranslationOverlay;

/**
 * 「译」在线流式字幕浮层：开/关 {@link RealtimeSubtitleSession}。
 */
public final class StreamingSubtitleOverlayPresenter {

    private final Handler main = new Handler(Looper.getMainLooper());
    @Nullable
    private RealtimeSubtitleSession session;
    @Nullable
    private DraggableTranslationOverlay overlay;
    private boolean visible;
    private long playbackPositionMs;

    public void setSession(@Nullable RealtimeSubtitleSession session) {
        this.session = session;
    }

    public void setPlaybackPositionMs(long playbackPositionMs) {
        this.playbackPositionMs = playbackPositionMs;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean toggle() {
        if (session == null) {
            ToastUtil.showShort(R.string.favorite_translation_no_session);
            return false;
        }
        if (!session.isConfigured()) {
            ToastUtil.showShort(R.string.volcano_asr_not_configured);
            return false;
        }
        ViewGroup parent = session.getOverlayParent();
        if (parent == null) {
            ToastUtil.showShort(R.string.favorite_translation_no_session);
            return false;
        }
        if (visible || session.isStreaming()) {
            visible = false;
            session.stopStreaming();
            detachOverlay();
            return false;
        }
        visible = true;
        if (overlay == null) {
            overlay = new DraggableTranslationOverlay(parent.getContext());
        }
        overlay.attachTo(parent);
        overlay.setLoading(true);
        overlay.setBodyText("");
        session.startStreaming(playbackPositionMs, callbacks);
        return true;
    }

    public void detach() {
        visible = false;
        if (session != null) {
            session.stopStreaming();
        }
        detachOverlay();
    }

    private void detachOverlay() {
        if (overlay != null) {
            overlay.cancelTypewriter();
            overlay.detachFromParent();
        }
    }

    private final RealtimeSubtitleCallbacks callbacks = new RealtimeSubtitleCallbacks() {
        @Override
        public void onLoading() {
            main.post(() -> {
                if (!visible || overlay == null) {
                    return;
                }
                overlay.setLoading(true);
            });
        }

        @Override
        public void onPartialText(@NonNull String text) {
            main.post(() -> {
                if (!visible || overlay == null) {
                    return;
                }
                overlay.setLoading(false);
                overlay.setBodyText(text);
            });
        }

        @Override
        public void onError(@NonNull String message) {
            main.post(() -> {
                if (!visible || overlay == null) {
                    return;
                }
                overlay.setLoading(false);
                overlay.setBodyText(message);
                ToastUtil.showShort(message);
            });
        }

        @Override
        public void onStopped() {
            main.post(() -> {
                visible = false;
                detachOverlay();
            });
        }
    };
}
