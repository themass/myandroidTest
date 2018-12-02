package com.qq.vpn.ui.base.actvity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.qq.ext.view.SlidingLayout;


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
