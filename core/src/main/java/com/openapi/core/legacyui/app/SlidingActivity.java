package com.openapi.core.legacyui.app;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.openapi.common.view.view.SlidingLayout;

public abstract class SlidingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
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
        return 250;
    }
}
