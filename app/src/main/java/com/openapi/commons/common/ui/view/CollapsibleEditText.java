package com.openapi.commons.common.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;

import com.openapi.ks.chatfree.R;

public class CollapsibleEditText extends androidx.appcompat.widget.AppCompatEditText {
    private boolean isExpanded = false; // 用于跟踪EditText的展开状态

    public CollapsibleEditText(Context context) {
        super(context);
        init();
    }

    public CollapsibleEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CollapsibleEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 设置可点击
        setClickable(true);
        // 设置绘制边框，以便用户知道可以点击
        setBackground(ContextCompat.getDrawable(getContext(), R.drawable.border));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 处理点击事件
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (!isExpanded) {
                // 展开EditText
                setHeightToMatchParent();
                isExpanded = true;
            } else {
                // 折叠EditText
                setHeightToMinimum();
                isExpanded = false;
            }
            invalidate(); // 重绘视图
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void setHeightToMatchParent() {
        // 设置高度为父视图的高度
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        setLayoutParams(layoutParams);
    }

    private void setHeightToMinimum() {
        // 设置最小高度，例如两行文本的高度
        int minHeight = (int) (getResources().getDimension(R.dimen.text_view_min_height) * 2);
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        layoutParams.height = minHeight;
        setLayoutParams(layoutParams);
    }
}