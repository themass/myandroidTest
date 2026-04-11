package com.openai.commonview.view;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.openai.commonevent.FavoriteVo;
import com.openai.commonview.translation.TranslationOverlaySession;
import com.openai.commonview.translation.VideoTranslationCallback;
import com.openai.commonview.translation.VideoTranslationRegistry;
import com.openai.commonview.translation.VideoTranslationRequest;
import com.openai.commonview.translation.VideoTranslationResult;
import com.openai.commonview.view.widget.DraggableTranslationOverlay;
import com.openai.commonutil.PackageUtils;
import com.openai.commonutil.R;
import com.openai.commonutil.SystemUtils;
import com.openai.commonutil.ToastUtil;

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
    private TranslationOverlaySession translationSession;
    private DraggableTranslationOverlay translationOverlay;
    private boolean translationVisible;
    private int translateEpoch;

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
        this.translationSession = session;
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
        if (translationSession == null) {
            ToastUtil.showShort(R.string.favorite_translation_no_session);
            return;
        }
        ViewGroup parent = translationSession.getOverlayParent();
        if (parent == null) {
            ToastUtil.showShort(R.string.favorite_translation_no_session);
            return;
        }
        if (translationVisible) {
            translateEpoch++;
            translationVisible = false;
            if (translationOverlay != null) {
                translationOverlay.detachFromParent();
            }
            return;
        }
        translationVisible = true;
        final int epoch = ++translateEpoch;
        if (translationOverlay == null) {
            translationOverlay = new DraggableTranslationOverlay(getContext());
        }
        translationOverlay.attachTo(parent);
        translationOverlay.setLoading(true);
        translationOverlay.setBodyText("");

        VideoTranslationRequest.Builder builder = new VideoTranslationRequest.Builder();
        try {
            translationSession.fillRequest(builder);
        } catch (Throwable t) {
            if (epoch != translateEpoch) {
                return;
            }
            translationOverlay.setLoading(false);
            translationOverlay.setBodyText(t.getMessage() != null ? t.getMessage() : "request build error");
            return;
        }
        final VideoTranslationRequest request;
        try {
            request = builder.build();
        } catch (IllegalStateException e) {
            if (epoch != translateEpoch) {
                return;
            }
            translationOverlay.setLoading(false);
            translationOverlay.setBodyText(e.getMessage());
            return;
        }

        VideoTranslationRegistry reg = translationSession.registry();
        if (reg == null) {
            reg = VideoTranslationRegistry.getInstance();
        }
        reg.translate(request, new VideoTranslationCallback() {
            @Override
            public void onSuccess(VideoTranslationResult result) {
                if (epoch != translateEpoch || !translationVisible || translationOverlay == null) {
                    return;
                }
                translationOverlay.setLoading(false);
                String t = result.getTranslatedText();
                translationOverlay.setBodyText(t != null ? t : "");
            }

            @Override
            public void onError(String message) {
                if (epoch != translateEpoch || !translationVisible || translationOverlay == null) {
                    return;
                }
                translationOverlay.setLoading(false);
                translationOverlay.setBodyText(message != null ? message : "");
            }
        });
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
