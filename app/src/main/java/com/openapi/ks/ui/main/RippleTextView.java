package com.openapi.ks.ui.main;

import android.animation.ValueAnimator;
import androidx.core.content.res.ResourcesCompat;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.provider.Settings;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.openapi.ks.free1.R;

/**
 * 开屏标题：文字本身做水波纹效果。
 * <p>
 * 波前从文字中心向两边推进，波前扫过的字先向上弹起，再像水面一样衰减回位，同时由透明变清晰；
 * 波前走完后所有字静止在原位。字色是白到浅紫的渐变，取自主题的强调色系。
 * 系统开启"减少动画"（动画时长为 0）时不播放，直接显示最终状态。
 */
public class RippleTextView extends View {

    /** 字的最大上下偏移（dp）。 */
    private static final float AMP_DP = 14f;
    /** 水波的波长（dp）：相邻波峰之间的距离。 */
    private static final float WAVE_DP = 32f;
    /** 波的衰减距离（dp）：离波前越远，起伏越小。 */
    private static final float DECAY_DP = 160f;
    /** 每个字从透明到清晰需要的波前距离（dp）。 */
    private static final float FADE_DP = 36f;

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private String text = "";
    /** 每个字符单独存一份，避免每帧 substring 产生临时对象。 */
    private String[] chars = new String[0];
    /** 每个字的左边界（相对文字起点）。 */
    private float[] charLeft = new float[0];
    private float textWidth;
    private float ascent;
    private float descent;
    private float progress = 1f;
    private ValueAnimator animator;

    public RippleTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        // 艺术字体（Great Vibes，OFL 开源字体），字形偏窄，所以水平拉伸一点、字距放开，显得更宽。
        Typeface art = ResourcesCompat.getFont(context, R.font.great_vibes);
        paint.setTypeface(art != null ? art : Typeface.create("serif", Typeface.ITALIC));
        paint.setTextSize(58 * density);
        paint.setTextScaleX(1.15f);
        paint.setLetterSpacing(0.08f);
    }

    /** 设置标题文字。 */
    public void setText(CharSequence value) {
        text = value == null ? "" : value.toString();
        int n = text.length();
        chars = new String[n];
        charLeft = new float[n];
        for (int i = 0; i < n; i++) {
            chars[i] = text.substring(i, i + 1);
            charLeft[i] = paint.measureText(text, 0, i);
        }
        textWidth = paint.measureText(text);
        android.graphics.Paint.FontMetrics fm = paint.getFontMetrics();
        ascent = fm.ascent;
        descent = fm.descent;
        requestLayout();
        invalidate();
    }

    /** 播放一次水波纹，结束后停在静止状态。 */
    public void play(long durationMs) {
        cancel();
        if (isReduceMotion()) {
            progress = 1f;
            invalidate();
            return;
        }
        progress = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(durationMs);
        animator.setInterpolator(new DecelerateInterpolator(1.2f));
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    /** 立即停止动画（页面离开时调用）。 */
    public void cancel() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private boolean isReduceMotion() {
        return Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    private float padH() {
        return AMP_DP * density * 2;
    }

    private float padV() {
        return AMP_DP * density * 1.5f;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = (int) Math.ceil(textWidth + padH());
        int h = (int) Math.ceil(descent - ascent + padV() * 2);
        setMeasuredDimension(
                resolveSize(w, widthMeasureSpec),
                resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // 白 -> 浅灰紫 -> 强调色的浅色端，横向渐变。
        paint.setShader(new LinearGradient(0, 0, Math.max(1, textWidth), 0,
                new int[]{0xFFA48BFF, 0xFF7C5CFF, 0xFF5A36E8},
                new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = text.length();
        if (n == 0) {
            return;
        }
        float left = padH() / 2;
        float baseline = padV() - ascent;
        float center = textWidth / 2;
        float amp = AMP_DP * density;
        float wave = WAVE_DP * density;
        float decay = DECAY_DP * density;
        float fade = FADE_DP * density;

        // 波前半径：从 0 推到整段文字之外，末段减速。
        float maxDist = center + fade;
        float ease = 1f - (1f - progress) * (1f - progress);
        float front = maxDist * ease;

        // 弹起量在动画末段平滑收到 0，保证最后一帧与静止状态完全一致，不会跳动。
        float settle = smoothstep(0.80f, 1f, progress);
        float ringScale = 1f - settle;

        for (int i = 0; i < n; i++) {
            float x = left + charLeft[i];
            if (progress >= 1f) {
                paint.setAlpha(255);
                canvas.drawText(chars[i], x, baseline, paint);
                continue;
            }
            float charCenter = charLeft[i] + paint.measureText(chars[i]) / 2;
            float dist = Math.abs(charCenter - center);
            float t = front - dist;     // >0 表示波前已经扫过这个字
            if (t <= 0f) {
                continue;
            }
            float bounce = amp * ringScale * (float) Math.sin(t / wave) * (float) Math.exp(-t / decay);
            float alpha = Math.min(1f, t / fade);
            paint.setAlpha((int) (alpha * 255));
            canvas.drawText(chars[i], x, baseline - bounce, paint);
        }
        paint.setAlpha(255);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }
}
