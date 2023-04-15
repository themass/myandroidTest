package com.openapi.common.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;

public class NImageView extends NetworkImageView {
    private ImageTask mImageTask; // 加载任务task
    private float mRatio; //长宽比例

    private Bitmap mLocalBitmap;

    private boolean mShowLocal;

    public NImageView(Context context) {
        super(context);
    }

    public NImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setLocalImageBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            mShowLocal = true;
        }
        this.mLocalBitmap = bitmap;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (mRatio > 0) {
            final int measureWidth = getMeasuredWidth();
            int measureHeight = (int) (measureWidth * mRatio);
            setMeasuredDimension(measureWidth, measureHeight);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        checkImageTask();
        if (mShowLocal) {
            setImageBitmap(mLocalBitmap);
        }
    }

    @Override
    public void setImageUrl(String url, ImageLoader imageLoader) {
        mShowLocal = false;
        mImageTask = new ImageTask();
        mImageTask.mUrl = url;
        mImageTask.mImageLoader = imageLoader;
        checkImageTask();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mLocalBitmap != null) {
            mLocalBitmap.recycle();
        }
    }

    private void checkImageTask() {
        if (mImageTask == null || getWidth() <= 0) {
            return;
        }
        String url = mImageTask.mUrl;
        if (!TextUtils.isEmpty(url)) {
            super.setImageUrl(url, mImageTask.mImageLoader);
        }
        mImageTask = null;
    }

    /**
     * 设置宽高比例<br/>
     * 例: 10 / 100f  则高是 10 宽100
     *
     * @param ratio
     */
    public void setRatio(float ratio) {
        if (mRatio != ratio) {
            mRatio = ratio;
            requestLayout();
        }
    }

    private static class ImageTask {
        private ImageLoader mImageLoader;
        private String mUrl;
    }
}
