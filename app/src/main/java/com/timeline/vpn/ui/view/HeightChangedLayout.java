package com.timeline.vpn.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

public class HeightChangedLayout extends RelativeLayout {
    private final static String TAG = "HeightChangedLinearLayout";

    private OnSoftChangedListener l;

    public HeightChangedLayout(Context context) {
        super(context);
    }

    public HeightChangedLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (l != null && oldh != 0) {
            if (h - oldh > 400) {
                l.onSoftKetbordHide();
            } else {
                l.onSoftkeybordShow();
            }
        }
        // new Thread(new Runnable() {
        // @Override
        // public void run() {
        // if (mLayoutSizeChangedListener != null) {
        // mLayoutSizeChangedListener.onLayoutSizeChanged(fw, fh,
        // foldw, foldh);
        // }
        // }
        // }).start();
    }

    public void setOnSoftKeybordChangeLisener(OnSoftChangedListener l) {
        this.l = l;
    }

    public interface OnSoftChangedListener {
        public void onSoftkeybordShow();

        public void onSoftKetbordHide();
    }

}
