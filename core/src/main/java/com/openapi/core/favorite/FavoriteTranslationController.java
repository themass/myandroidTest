package com.openapi.core.favorite;

import androidx.annotation.Nullable;

import com.openapi.common.util.android.ToastUtil;
import com.openapi.core.R;
import com.openapi.subtitle.api.RealtimeSubtitleSession;
import com.openapi.subtitle.api.StreamingSubtitleOverlayPresenter;
import com.openapi.subtitle.api.TranslationOverlayPresenter;
import com.openapi.subtitle.api.TranslationOverlaySession;

/**
 * 收藏条上「译」按钮的逻辑（从 {@link MyFavoriteView} 拆出）：在线流式字幕（火山 ASR）优先，
 * 没有配置时走普通的翻译浮层。视图只负责把点击转发到这里。
 */
public class FavoriteTranslationController {

    private TranslationOverlayPresenter translationPresenter;
    private StreamingSubtitleOverlayPresenter streamingPresenter;
    private RealtimeSubtitleSession realtimeSubtitleSession;
    @Nullable
    private MyFavoriteView.StreamingPositionProvider streamingPositionProvider;

    public void setStreamingPositionProvider(@Nullable MyFavoriteView.StreamingPositionProvider provider) {
        this.streamingPositionProvider = provider;
    }

    /**
     * 播放页设置后，「译」按钮可显示翻译字幕浮层并走 VideoTranslationRegistry。
     */
    public void setTranslationSession(TranslationOverlaySession session) {
        if (translationPresenter == null) {
            translationPresenter = new TranslationOverlayPresenter();
        }
        translationPresenter.setSession(session);
    }

    /**
     * 在线流式字幕（火山 ASR）。已配置时，「译」优先走此路径。
     */
    public void setRealtimeSubtitleSession(RealtimeSubtitleSession session) {
        this.realtimeSubtitleSession = session;
        if (streamingPresenter == null) {
            streamingPresenter = new StreamingSubtitleOverlayPresenter();
        }
        streamingPresenter.setSession(session);
    }

    /** 点「译」前更新当前播放进度（毫秒）。 */
    public void setStreamingPlaybackPositionMs(long positionMs) {
        if (streamingPresenter == null) {
            streamingPresenter = new StreamingSubtitleOverlayPresenter();
        }
        streamingPresenter.setPlaybackPositionMs(positionMs);
    }

    /** 页面销毁时收起字幕浮层，避免持有旧 View 树。 */
    public void release() {
        if (translationPresenter != null) {
            translationPresenter.detach();
        }
        if (streamingPresenter != null) {
            streamingPresenter.detach();
        }
    }

    /** 点「译」：切换字幕浮层。 */
    public void toggle() {
        if (realtimeSubtitleSession != null) {
            if (!realtimeSubtitleSession.isConfigured()) {
                ToastUtil.showShort(R.string.volcano_asr_not_configured);
                return;
            }
            if (streamingPresenter == null) {
                streamingPresenter = new StreamingSubtitleOverlayPresenter();
            }
            streamingPresenter.setSession(realtimeSubtitleSession);
            if (streamingPositionProvider != null) {
                streamingPresenter.setPlaybackPositionMs(streamingPositionProvider.getPlaybackPositionMs());
            }
            streamingPresenter.toggle();
            return;
        }
        if (translationPresenter == null) {
            translationPresenter = new TranslationOverlayPresenter();
        }
        if (translationPresenter.getSession() == null) {
            ToastUtil.showShort(R.string.favorite_translation_no_session);
            return;
        }
        translationPresenter.toggle();
    }
}
