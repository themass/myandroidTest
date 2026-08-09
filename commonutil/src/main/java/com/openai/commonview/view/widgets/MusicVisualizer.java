package com.openai.commonview.view.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.Random;

/**
 * 简易三竖条频谱动画。经典模式为随机高度；Auxio 模式为底部对齐、左高中低基线 + 正弦起伏（贴近 Auxio 列表项）。
 */
public class MusicVisualizer extends View {
    /** 由 Adapter 显式 {@link #startAnima()} / {@link #stopAnima()}，避免未绑定行也一直 post。 */
    public boolean startAnima = false;

    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean auxioStyle;
    private long auxioStartElapsed;

    private final Runnable animateView = new Runnable() {
        @Override
        public void run() {
            if (!startAnima) {
                return;
            }
            invalidate();
            postDelayed(this, auxioStyle ? 32 : 150);
        }
    };

    public MusicVisualizer(Context context) {
        this(context, null);
    }

    public void startAnima() {
        startAnima = true;
        if (auxioStyle) {
            auxioStartElapsed = SystemClock.elapsedRealtime();
        }
        removeCallbacks(animateView);
        post(animateView);
    }

    public void stopAnima() {
        startAnima = false;
        removeCallbacks(animateView);
    }

    /**
     * Auxio 列表：黑条、底对齐、相位动画；关闭后恢复旧版随机竖条（用于非 Auxio item 布局）。
     */
    public void setAuxioStyle(boolean auxio) {
        this.auxioStyle = auxio;
        if (auxio) {
            auxioStartElapsed = SystemClock.elapsedRealtime();
        }
        invalidate();
    }

    public MusicVisualizer(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (startAnima) {
            removeCallbacks(animateView);
            post(animateView);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(animateView);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (auxioStyle) {
            drawAuxioBars(canvas);
        } else {
            drawRandomBars(canvas);
        }
    }

    /** Auxio：左条最高、中条约一半、右条最短，整体自底向上“跳动”。 */
    private void drawAuxioBars(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 1f || h <= 1f) {
            return;
        }
        float padX = dpToPx(10f);
        float padY = dpToPx(10f);
        float innerW = w - 2f * padX;
        float innerH = h - 2f * padY;
        float barW = dpToPx(3.5f);
        float gap = (innerW - 3f * barW) / 2f;
        if (gap < 0f) {
            gap = 0f;
        }

        long t = SystemClock.elapsedRealtime() - auxioStartElapsed;
        // 约 520ms 一整周，配合 32ms 刷新，跳动比原先 ~980ms+48ms 更利落
        float phase = (t / 520f) * ((float) (Math.PI * 2d));

        float[] baseRatio = {1f, 0.52f, 0.26f};
        for (int i = 0; i < 3; i++) {
            float wave = 0.5f + 0.5f * (float) Math.sin(phase + i * 1.25f);
            float barH = innerH * baseRatio[i] * (0.38f + 0.62f * wave);
            barH = Math.max(dpToPx(4f), Math.min(innerH, barH));
            float left = padX + i * (barW + gap);
            float top = padY + innerH - barH;
            canvas.drawRect(left, top, left + barW, padY + innerH, paint);
        }
    }

    private void drawRandomBars(Canvas canvas) {
        int maxRand = Math.max(1, (int) (getHeight() / 1.5f) - 19);
        canvas.drawRect(
                getDimensionInPixel(0),
                getHeight() - (20 + random.nextInt(maxRand)),
                getDimensionInPixel(7),
                getHeight(),
                paint);
        canvas.drawRect(
                getDimensionInPixel(10),
                getHeight() - (20 + random.nextInt(maxRand)),
                getDimensionInPixel(17),
                getHeight(),
                paint);
        canvas.drawRect(
                getDimensionInPixel(20),
                getHeight() - (20 + random.nextInt(maxRand)),
                getDimensionInPixel(27),
                getHeight(),
                paint);
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    private int getDimensionInPixel(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
