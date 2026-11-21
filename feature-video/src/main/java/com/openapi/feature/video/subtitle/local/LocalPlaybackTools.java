package com.openapi.feature.video.subtitle.local;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.openapi.feature.video.R;
import com.openapi.feature.video.player.DetailGSYVideoPlayer;
import com.shuyu.gsyvideoplayer.video.base.GSYBaseVideoPlayer;
import com.shuyu.gsyvideoplayer.video.base.GSYVideoView;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Activity-owned prototype; the rendering view follows GSY's active fullscreen instance. */
public final class LocalPlaybackTools implements AutoCloseable {
    private final AppCompatActivity activity;
    private final DetailGSYVideoPlayer root;
    private final Runnable importDocument;
    private final Runnable cast;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Runnable tick = this::update;
    private FrameLayout overlay;
    private TextView subtitle;
    private GSYBaseVideoPlayer attachedPlayer;
    private Future<?> pendingImport;
    private int importGeneration;
    private int delayMs;
    private boolean running;
    private boolean closed;
    private boolean enabled;
    private LocalSubtitleTrack track;
    private String lastText = "";
    private AlertDialog dialog;

    public LocalPlaybackTools(AppCompatActivity activity, DetailGSYVideoPlayer root,
                              Runnable importDocument, Runnable cast) {
        this.activity = activity;
        this.root = root;
        this.importDocument = importDocument;
        this.cast = cast;
    }

    public void resume() {
        if (closed || running) return;
        running = true;
        update();
    }

    public void pause() {
        running = false;
        ui.removeCallbacks(tick);
    }

    public void importSubtitle(@Nullable Uri uri) {
        if (closed || uri == null) return;
        if (pendingImport != null) pendingImport.cancel(true);
        int generation = ++importGeneration;
        pendingImport = io.submit(() -> {
            try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("No input stream");
                LocalSubtitleTrack parsed = LocalSubtitleTrack.read(
                        new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()));
                ui.post(() -> {
                    if (closed || generation != importGeneration) return;
                    track = parsed;
                    delayMs = 0;
                    enabled = true;
                    Toast.makeText(activity, R.string.local_subtitle_loaded, Toast.LENGTH_SHORT).show();
                });
            } catch (IOException | RuntimeException failure) {
                ui.post(() -> {
                    if (closed || generation != importGeneration) return;
                    Toast.makeText(activity, R.string.local_subtitle_invalid, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void update() {
        if (!running) return;
        GSYBaseVideoPlayer player = root.getCurrentPlayer();
        if (player != attachedPlayer) mount(player);
        int state = player.getCurrentState();
        boolean hasPosition = state == GSYVideoView.CURRENT_STATE_PLAYING
                || state == GSYVideoView.CURRENT_STATE_PAUSE
                || state == GSYVideoView.CURRENT_STATE_PLAYING_BUFFERING_START;
        String text = enabled && track != null && hasPosition
                ? track.at(player.getCurrentPositionWhenPlaying(), delayMs) : "";
        if (!text.equals(lastText)) {
            lastText = text;
            subtitle.setText(text);
            subtitle.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        }
        ui.postDelayed(tick, enabled ? 80 : 180);
    }

    private void mount(GSYBaseVideoPlayer player) {
        if (attachedPlayer != null) attachedPlayer.removeView(overlay);
        attachedPlayer = player;
        if (player instanceof DetailGSYVideoPlayer detail) {
            detail.getMyFavoriteView().setVisibility(View.GONE);
        }
        Context context = player.getContext();
        overlay = new FrameLayout(context);
        subtitle = new TextView(context);
        subtitle.setTextColor(Color.WHITE);
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setMaxLines(4);
        subtitle.setPadding(dp(8), dp(4), dp(8), dp(4));
        subtitle.setBackground(chipBackground(0xd9000000));
        subtitle.setVisibility(View.GONE);
        lastText = "";
        FrameLayout.LayoutParams captionPosition = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        captionPosition.setMargins(dp(16), 0, dp(16), dp(112));
        overlay.addView(subtitle, captionPosition);

        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.END);
        bar.addView(button(context, R.string.local_subtitle_title, this::showSubtitleOptions, false));
        bar.addView(button(context, R.string.local_tools_speed, this::showSpeeds, false));
        bar.addView(button(context, R.string.local_tools_favorite,
                () -> root.getMyFavoriteView().favoriteClick(root.getMyFavoriteView()), true));
        bar.addView(button(context, R.string.local_tools_cast, cast, true));
        FrameLayout.LayoutParams barPosition = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        barPosition.setMarginEnd(dp(8));
        barPosition.bottomMargin = dp(52);
        overlay.addView(bar, barPosition);
        player.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private Button button(Context context, int label, Runnable action, boolean favorite) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(favorite ? 0xff132a21 : Color.WHITE);
        button.setBackground(chipBackground(favorite ? 0xffc3f3dc : 0xe6232b30));
        button.setBackgroundTintList((ColorStateList) null);
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(48));
        params.setMarginStart(dp(4));
        button.setLayoutParams(params);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private GradientDrawable chipBackground(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(12));
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void showSubtitleOptions() {
        if (dialog != null) dialog.dismiss();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.local_subtitle_title)
                .setNegativeButton(android.R.string.cancel, null);
        if (track == null) {
            builder.setItems(new String[]{activity.getString(R.string.local_subtitle_import)},
                    (d, i) -> importDocument.run());
        } else {
            builder.setItems(new String[]{activity.getString(R.string.local_subtitle_import),
                    activity.getString(enabled ? R.string.local_subtitle_hide : R.string.local_subtitle_show),
                    activity.getString(R.string.local_subtitle_delay, delayMs)}, (d, i) -> {
                if (i == 0) importDocument.run();
                else if (i == 1) enabled = !enabled;
                else showDelay();
            });
        }
        dialog = builder.show();
    }

    private void showDelay() {
        dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.local_subtitle_delay, delayMs))
                .setMessage(R.string.local_subtitle_delay_hint)
                .setNegativeButton(android.R.string.ok, null)
                .setNeutralButton("−500 ms", (d, i) -> adjustDelay(-500))
                .setPositiveButton("+500 ms", (d, i) -> adjustDelay(500))
                .show();
    }

    private void adjustDelay(int delta) {
        delayMs = Math.max(-60_000, Math.min(60_000, delayMs + delta));
        showDelay();
    }

    private void showSpeeds() {
        if (dialog != null) dialog.dismiss();
        float[] speeds = {0.75f, 1f, 1.25f, 1.5f, 2f};
        dialog = new AlertDialog.Builder(activity).setTitle(R.string.local_tools_speed)
                .setItems(new String[]{"0.75×", "1×", "1.25×", "1.5×", "2×"}, (d, i) ->
                        root.getCurrentPlayer().setSpeed(speeds[i], true)).show();
    }

    @Override public void close() {
        closed = true;
        pause();
        if (dialog != null) dialog.dismiss();
        ui.removeCallbacksAndMessages(null);
        if (pendingImport != null) pendingImport.cancel(true);
        io.shutdownNow();
        if (attachedPlayer != null) attachedPlayer.removeView(overlay);
    }
}
