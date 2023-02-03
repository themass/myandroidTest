package com.sspacee.common.ui.view;

import android.content.Context;
import androidx.appcompat.widget.AppCompatButton;
import android.util.AttributeSet;

import com.ks.myapp.base.MyApplication;

/**
 * 自定义字体的Button
 *
 * @author hlwang
 */
public class MyButton extends AppCompatButton {

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public MyButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * @param context
     * @param attrs
     */
    public MyButton(Context context, AttributeSet attrs) {
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
    public MyButton(Context context) {
        super(context);
    }

}
