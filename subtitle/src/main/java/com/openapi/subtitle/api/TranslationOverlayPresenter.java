package com.openapi.subtitle.api;

import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.subtitle.R;
import com.openapi.subtitle.overlay.DraggableTranslationOverlay;

/**
 * 可复用的「译」字幕浮层：任意页面注入 {@link TranslationOverlaySession} 后调用 {@link #toggle()}。
 * 与 {@link com.openapi.core.favorite.MyFavoriteView} 解耦，便于音乐详情、其他容器复用同一套逻辑。
 */
public final class TranslationOverlayPresenter {

    @Nullable
    private TranslationOverlaySession session;
    @Nullable
    private DraggableTranslationOverlay overlay;
    private boolean visible;
    private int epoch;

    public TranslationOverlayPresenter() {
    }

    public void setSession(@Nullable TranslationOverlaySession session) {
        this.session = session;
    }

    @Nullable
    public TranslationOverlaySession getSession() {
        return session;
    }

    public boolean isVisible() {
        return visible;
    }

    /**
     * 显示则关闭浮层；关闭则发起翻译并展示浮层。
     *
     * @return 关闭操作后为不可见时返回 false，打开展示后为 true
     */
    public boolean toggle() {
        if (session == null) {
            ToastUtil.showShort(R.string.favorite_translation_no_session);
            return false;
        }
        ViewGroup parent = session.getOverlayParent();
        if (parent == null) {
            ToastUtil.showShort(R.string.favorite_translation_no_session);
            return false;
        }
        if (visible) {
            epoch++;
            visible = false;
            if (overlay != null) {
                overlay.detachFromParent();
            }
            return false;
        }
        visible = true;
        final int ep = ++epoch;
        if (overlay == null) {
            overlay = new DraggableTranslationOverlay(parent.getContext());
        }
        overlay.attachTo(parent);
        overlay.setLoading(true);
        overlay.setBodyText("");

        VideoTranslationRequest.Builder builder = new VideoTranslationRequest.Builder();
        try {
            session.fillRequest(builder);
        } catch (Throwable t) {
            if (ep != epoch) {
                return false;
            }
            overlay.setLoading(false);
            overlay.setBodyText(t.getMessage() != null ? t.getMessage() : "request build error");
            LogUtil.e(t);
            return true;
        }
        final VideoTranslationRequest request;
        try {
            request = builder.build();
        } catch (IllegalStateException e) {
            if (ep != epoch) {
                return false;
            }
            overlay.setLoading(false);
            overlay.setBodyText(e.getMessage());
            return true;
        }

        VideoTranslationRegistry reg = session.registry();
        if (reg == null) {
            reg = VideoTranslationRegistry.getInstance();
        }
        reg.translate(request, new VideoTranslationCallback() {
            @Override
            public void onSuccess(VideoTranslationResult result) {
                if (ep != epoch || !visible || overlay == null) {
                    return;
                }
                overlay.setLoading(false);
                String t = result.getTranslatedText();
                overlay.setBodyTextTypewriter(t != null ? t : "");
            }

            @Override
            public void onError(String message) {
                if (ep != epoch || !visible || overlay == null) {
                    return;
                }
                overlay.setLoading(false);
                overlay.setBodyText(message != null ? message : "");
            }
        });
        return true;
    }

    /** Activity / Fragment 销毁时调用，避免泄漏。 */
    public void detach() {
        epoch++;
        visible = false;
        if (overlay != null) {
            overlay.cancelTypewriter();
            overlay.detachFromParent();
        }
    }
}
