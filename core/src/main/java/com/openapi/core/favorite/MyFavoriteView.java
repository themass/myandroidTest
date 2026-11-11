package com.openapi.core.favorite;

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

import com.openapi.core.db.FavoriteVo;
import com.openapi.ks.myapp.translation.api.RealtimeSubtitleSession;
import com.openapi.ks.myapp.translation.api.TranslationOverlaySession;
import com.openapi.common.util.android.PackageUtils;
import com.openapi.core.R;
import com.openapi.core.databinding.DialogFavoriteExternalLinesBinding;
import com.openapi.core.databinding.LayoutFavoriteBarBinding;
import com.openapi.common.util.android.SystemUtils;
import com.openapi.common.util.android.ToastUtil;

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
    /** 「译」按钮的逻辑（翻译浮层、在线流式字幕）。 */
    private final FavoriteTranslationController translation = new FavoriteTranslationController();

    /** 点「译」开流式字幕前读取当前播放进度。 */
    public interface StreamingPositionProvider {
        long getPlaybackPositionMs();
    }

    public void setStreamingPositionProvider(@Nullable StreamingPositionProvider provider) {
        translation.setStreamingPositionProvider(provider);
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

    /** 播放页设置后，「译」按钮可显示翻译字幕浮层并走 VideoTranslationRegistry。 */
    public void setTranslationSession(TranslationOverlaySession session) {
        translation.setTranslationSession(session);
    }

    /** 在线流式字幕（火山 ASR）。已配置时，「译」优先走此路径。 */
    public void setRealtimeSubtitleSession(RealtimeSubtitleSession session) {
        translation.setRealtimeSubtitleSession(session);
    }

    /** 点「译」前更新当前播放进度（毫秒）。 */
    public void setStreamingPlaybackPositionMs(long positionMs) {
        translation.setStreamingPlaybackPositionMs(positionMs);
    }

    /** 页面销毁时收起字幕浮层，避免持有旧 View 树。 */
    public void releaseTranslationOverlay() {
        translation.release();
    }

    private void setupView() {
        LayoutInflater mInflater = LayoutInflater.from(getContext());
        LayoutFavoriteBarBinding b = LayoutFavoriteBarBinding.inflate(mInflater);
        addView(b.getRoot());
        ivFavorite = b.ivFavorite;
        ivBrowser = b.ivOpenBrowser;
        ivM3u8Lines = b.ivM3u8Lines;
        tvTranslate = b.tvTranslate;
        speed = b.speed;
        ivFavorite.setOnClickListener(v -> favoriteClick(v));
        ivBrowser.setOnClickListener(v -> browserClick(v));
        ivM3u8Lines.setOnClickListener(v -> showM3u8LineMenu(v));
        tvTranslate.setOnClickListener(v -> translation.toggle());
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
        DialogFavoriteExternalLinesBinding sb = DialogFavoriteExternalLinesBinding.inflate(LayoutInflater.from(ctx));
        View sheet = sb.getRoot();
        TextView line1 = sb.tvExternalLine1;
        TextView line2 = sb.tvExternalLine2;
        TextView cancel = sb.tvSheetCancel;
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
