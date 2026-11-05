package com.openapi.core.view.view;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import androidx.annotation.Nullable;

import com.openapi.core.event.FavoriteVo;
import com.openapi.core.view.translation.RealtimeSubtitleSession;
import com.openapi.core.view.translation.StreamingSubtitleOverlayPresenter;
import com.openapi.core.view.translation.TranslationOverlayPresenter;
import com.openapi.core.view.translation.TranslationOverlaySession;
import com.openapi.core.util.PackageUtils;
import com.openapi.core.util.R;
import com.openapi.core.util.SystemUtils;
import com.openapi.core.util.ToastUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Created by openapi on 2016/9/6.
 */
public class MyFavoriteView extends LinearLayout {

    public static String url_pre1 = "https://www.xcvods.com/addons/dplayer/?url=";
    public static String url_pre2 = "http://file.ok123find.top/index2.html?play=";
    int currentSpeedIndex = 0;
    float[] speedX = {1.0f, 1.25f, 1.5f, 1.75f, 2f, 3f};
    FavoriteImageView ivFavorite;
    ImageView ivBrowser;
    ImageView ivM3u8Lines;
    TextView tvTranslate;
    TextView speed;
    private OnFavoriteItemClick onFavoriteItemClick;
    private TranslationOverlayPresenter translationPresenter;
    private StreamingSubtitleOverlayPresenter streamingPresenter;
    private RealtimeSubtitleSession realtimeSubtitleSession;
    @Nullable
    private StreamingPositionProvider streamingPositionProvider;

    /** 点「译」开流式字幕前读取当前播放进度。 */
    public interface StreamingPositionProvider {
        long getPlaybackPositionMs();
    }

    public void setStreamingPositionProvider(@Nullable StreamingPositionProvider provider) {
        this.streamingPositionProvider = provider;
    }

    public MyFavoriteView(Context context) {
        super(context);
        setupView();
    }

    public MyFavoriteView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupView();
    }

    public MyFavoriteView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setupView();
    }

    public void setListener(MyFavoriteView.OnFavoriteItemClick listener) {
        this.onFavoriteItemClick = listener;
    }

    /**
     * 播放页设置后，「译」按钮可显示翻译字幕浮层并走 {@link VideoTranslationRegistry}。
     */
    public void setTranslationSession(TranslationOverlaySession session) {
        if (translationPresenter == null) {
            translationPresenter = new TranslationOverlayPresenter();
        }
        translationPresenter.setSession(session);
    }

    /**
     * 在线流式字幕（火山 ASR）。已配置且 {@link #isConfigured()} 时，「译」优先走此路径。
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
    public void releaseTranslationOverlay() {
        if (translationPresenter != null) {
            translationPresenter.detach();
        }
        if (streamingPresenter != null) {
            streamingPresenter.detach();
        }
    }

    private void setupView() {
        LayoutInflater mInflater = LayoutInflater.from(getContext());
        View myView = mInflater.inflate(R.layout.layout_favorite_bar, null);
        addView(myView);
        ivFavorite = findViewById(R.id.iv_favorite);
        ivBrowser = findViewById(R.id.iv_open_browser);
        ivM3u8Lines = findViewById(R.id.iv_m3u8_lines);
        tvTranslate = findViewById(R.id.tv_translate);
        speed = findViewById(R.id.speed);
        ivFavorite.setOnClickListener(v -> favoriteClick(v));
        ivBrowser.setOnClickListener(v -> browserClick(v));
        ivM3u8Lines.setOnClickListener(v -> showM3u8LineMenu(v));
        tvTranslate.setOnClickListener(v -> translateClick());
        speed.setOnClickListener(v -> speedClick(v));
    }

    public void initFavoriteBackGroud(String url) {
        ivFavorite.initSrc(url);
    }

    public void showVideoLocal() {
        ivM3u8Lines.setVisibility(View.VISIBLE);
        speed.setVisibility(View.VISIBLE);
        tvTranslate.setVisibility(View.VISIBLE);
        speed.setText(speedX[currentSpeedIndex] + "X");
    }

    private void translateClick() {
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

    public void favoriteClick(View view) {
        if (onFavoriteItemClick == null) {
            return;
        }
        ivFavorite.clickFavorite(onFavoriteItemClick.getFavoriteDataUrl());
    }

    public void speedClick(View view) {
        if (onFavoriteItemClick == null) {
            return;
        }
        int index = currentSpeedIndex;
        currentSpeedIndex = (currentSpeedIndex + 1) % speedX.length;
        boolean ret = onFavoriteItemClick.setSpeed(speedX[currentSpeedIndex]);
        if (!ret) {
            currentSpeedIndex = index;
            return;
        }
        speed.setText(speedX[currentSpeedIndex] + "X");
        ToastUtil.showShort(speedX[currentSpeedIndex] + "X");
    }

    public void browserClick(View view) {
        if (onFavoriteItemClick == null) {
            return;
        }
        SystemUtils.copy(getContext(), onFavoriteItemClick.getBrowserDataUrl());
        ToastUtil.showShort(R.string.menu_share_copy_ok);
        startBrower();
    }

    private void showM3u8LineMenu(View anchor) {
        if (onFavoriteItemClick == null) {
            ToastUtil.showShort(R.string.favorite_listener_missing);
            return;
        }
        Context ctx = getContext();
        View sheet = LayoutInflater.from(ctx).inflate(R.layout.dialog_favorite_external_lines, null);
        TextView line1 = sheet.findViewById(R.id.tv_external_line1);
        TextView line2 = sheet.findViewById(R.id.tv_external_line2);
        TextView cancel = sheet.findViewById(R.id.tv_sheet_cancel);
        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setView(sheet)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        line1.setOnClickListener(v -> {
            dialog.dismiss();
            openExternalM3u8Line(url_pre1);
        });
        line2.setOnClickListener(v -> {
            dialog.dismiss();
            openExternalM3u8Line(url_pre2);
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void openExternalM3u8Line(String prefix) {
        if (onFavoriteItemClick == null) {
            return;
        }
        String raw = onFavoriteItemClick.getBrowserDataUrl();
        SystemUtils.copy(getContext(), raw);
        ToastUtil.showShort(R.string.menu_share_copy_ok);
        String encoded = encodeStreamUrlForQuery(raw);
        String url = prefix + encoded;
        if (PackageUtils.hasBrowser(getContext())) {
            Uri uri = Uri.parse(url);
            Intent it = new Intent(Intent.ACTION_VIEW, uri);
            getContext().startActivity(it);
        }
    }

    static String encodeStreamUrlForQuery(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        try {
            return URLEncoder.encode(raw, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return Uri.encode(raw);
        }
    }

    public void startBrower() {
        if (onFavoriteItemClick == null) {
            return;
        }
        if (PackageUtils.hasBrowser(getContext())) {
            Uri uri = Uri.parse(onFavoriteItemClick.getBrowserDataUrl());
            Intent it = new Intent(Intent.ACTION_VIEW, uri);
            if (it.resolveActivity(getContext().getPackageManager()) != null) {
                getContext().startActivity(it);
            }
        } else {
            ToastUtil.showShort(R.string.no_browser);
        }
    }

    public interface OnFavoriteItemClick {

        FavoriteVo getFavoriteDataUrl();

        String getBrowserDataUrl();

        default boolean setSpeed(float speed) {
            return false;
        }

    }

}
