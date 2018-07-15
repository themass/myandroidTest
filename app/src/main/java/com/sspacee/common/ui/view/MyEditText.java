package com.sspacee.common.ui.view;

import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.timeline.view.base.MyApplication;

/**
 * 自定义字体的EditText
 *
 * @author hlwang
 */
public class MyEditText extends AppCompatEditText {

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public MyEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * @param context
     * @param attrs
     */
    public MyEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        try {
            setTypeface(MyApplication.getInstance().typeface);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @param context
     */
    public MyEditText(Context context) {
        super(context);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (action == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (action == MotionEvent.ACTION_UP) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        return super.onTouchEvent(event);
    }

}
