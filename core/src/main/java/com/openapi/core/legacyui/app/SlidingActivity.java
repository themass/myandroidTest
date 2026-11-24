package com.openapi.core.legacyui.app;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.openapi.common.ui.widget.SlidingLayout;

public abstract class SlidingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
        if (enableSliding()) {
            SlidingLayout rootView = new SlidingLayout(this);
            // B6'：0 表示用 SlidingLayout 的默认左缘区（24dp×density）；
            // 原来这里写死 250px，和拦截侧的 10% 屏宽两套口径打架
            int minTouchX = getMinTouchX();
            if (minTouchX > 0) {
                rootView.setmTouchDownInterceptX(minTouchX);
            }
            rootView.bindActivity(this);
        }
    }

    protected boolean enableSliding() {
        return false;
    }

    /** 左缘滑动返回的判定区宽度（px）；返回 ≤0 用 SlidingLayout 默认（24dp×density）。 */
    protected int getMinTouchX() {
        return 0;
    }
}
