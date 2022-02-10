package com.qq.common.ui.base;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import androidx.appcompat.app.AppCompatActivity;

import com.qq.common.ui.view.SlidingLayout;

public abstract class SlidingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (enableSliding()) {
            SlidingLayout rootView = new SlidingLayout(this);
            rootView.setmTouchDownInterceptX(getMinTouchX());
            rootView.bindActivity(this);
        }
    }

    protected boolean enableSliding() {
        return false;
    }

    protected int getMinTouchX() {
        return 200;
    }
}
