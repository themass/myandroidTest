package com.openai.commonview.view.widget;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.openai.commonutil.R;

/**
 * 盖在视频上的中文字幕条；按住顶部标题区域可拖动整体位置。
 */
public class DraggableTranslationOverlay extends FrameLayout {

    private final TextView tvBody;
    private final ProgressBar progress;
    private float lastRawX;
    private float lastRawY;
    private float startTransX;
    private float startTransY;

    public DraggableTranslationOverlay(Context context) {
        super(context);
        setBackgroundResource(R.drawable.translation_overlay_bg);
        int pad = dp(10);
        setPadding(pad, pad, pad, pad);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams colLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        addView(column, colLp);

        TextView tvHint = new TextView(context);
        tvHint.setText(R.string.favorite_translation_title);
        tvHint.setTextColor(0xFFFFFFFF);
        tvHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvHint.setPadding(0, 0, 0, dp(4));
        column.addView(tvHint, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        setupDragOnView(tvHint);

        progress = new ProgressBar(context);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        pLp.gravity = Gravity.CENTER_HORIZONTAL;
        column.addView(progress, pLp);

        ScrollView scroll = new ScrollView(context);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(200));
        column.addView(scroll, sLp);

        tvBody = new TextView(context);
        tvBody.setTextColor(0xFFEEEEEE);
        tvBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvBody.setLineSpacing(dp(2), 1f);
        scroll.addView(tvBody, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void setupDragOnView(View handle) {
        handle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastRawX = event.getRawX();
                    lastRawY = event.getRawY();
                    startTransX = DraggableTranslationOverlay.this.getTranslationX();
                    startTransY = DraggableTranslationOverlay.this.getTranslationY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - lastRawX;
                    float dy = event.getRawY() - lastRawY;
                    DraggableTranslationOverlay.this.setTranslationX(startTransX + dx);
                    DraggableTranslationOverlay.this.setTranslationY(startTransY + dy);
                    return true;
                default:
                    return false;
            }
        });
    }

    private int dp(int d) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, d,
                getResources().getDisplayMetrics());
    }

    public void setLoading(boolean loading) {
        progress.setVisibility(loading ? VISIBLE : GONE);
    }

    public void setBodyText(CharSequence text) {
        tvBody.setText(text);
    }

    public void attachTo(ViewGroup parent) {
        if (getParent() != null) {
            ((ViewGroup) getParent()).removeView(this);
        }
        if (parent instanceof FrameLayout) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.topMargin = dp(48);
            lp.leftMargin = dp(8);
            lp.rightMargin = dp(8);
            parent.addView(this, lp);
        } else {
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(48);
            lp.leftMargin = dp(8);
            lp.rightMargin = dp(8);
            parent.addView(this, lp);
        }
        bringToFront();
    }

    public void detachFromParent() {
        if (getParent() != null) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }
}
