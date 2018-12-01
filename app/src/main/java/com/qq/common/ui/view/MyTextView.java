package com.qq.common.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.support.v7.widget.AppCompatTextView;
import android.text.Layout;
import android.util.AttributeSet;

import com.qq.common.util.DensityUtil;
import com.qq.common.util.Tools;
import com.qq.myapp.base.MyApplication;

/**
 * 自定义TextView
 *
 * @author hailongw
 */
public class MyTextView extends AppCompatTextView {

    private static final String NAMESPACE = "http://www.ywlx.net/apk/res/easymobi";
    private static final String ATTR_ROTATE = "rotate";
    private static final int DEFAULTVALUE_DEGREES = 0;
    private int degrees;
    private TextScrollViewListener scrollViewListener = null;

    public MyTextView(Context context) {
        super(context);
    }

    public MyTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        degrees = attrs.getAttributeIntValue(NAMESPACE, ATTR_ROTATE, DEFAULTVALUE_DEGREES);
        try {
            setTypeface(MyApplication.getInstance().typeface);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void onDraw(Canvas canvas) {
        canvas.rotate(degrees, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
        super.onDraw(canvas);
    }

    public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        Tools.setTextViewLineExtra(this, DensityUtil.dip2px(getContext(), 5), 1);
    }

    public int getLineNum(int h) {
        Layout layout = getLayout();
        int topOfLastLine = (h - getPaddingTop()) / (getLineHeight() + (int) getLineSpacingMultiplier());
        return topOfLastLine;
//        return layout.getLineForVertical(topOfLastLine);
    }

    public void setScrollViewListener(TextScrollViewListener scrollViewListener) {
        this.scrollViewListener = scrollViewListener;
    }

    @Override
    protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
        if (scrollViewListener != null) {
            scrollViewListener.onScrollChanged(this, horiz, vert, oldHoriz, oldVert);
        }
    }

    public interface TextScrollViewListener {
        void onScrollChanged(MyTextView view, int x, int y, int oldx, int oldy);
    }
}
