package test;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class AudioWaveformView extends View {
    private Paint paint;
    private float[] waveform;

    public AudioWaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFF4CAF50); // 设置波形颜色
        paint.setStrokeWidth(4); // 设置波形宽度
    }

    public void setWaveform(float[] waveform) {
        this.waveform = waveform;
        invalidate(); // 重新绘制
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (waveform == null) return;

        float centerY = getHeight() / 2f;
        for (int i = 0; i < waveform.length; i++) {
            float amplitude = waveform[i] * getHeight() / 2f; // 根据波形数据计算振幅
            float x = getWidth() * i / (float) waveform.length; // 计算 x 坐标
            canvas.drawLine(x, centerY - amplitude, x, centerY + amplitude, paint);
        }
    }
}