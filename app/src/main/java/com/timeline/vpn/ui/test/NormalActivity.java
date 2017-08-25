package com.timeline.vpn.ui.test;

import android.os.Bundle;

import com.sspacee.common.ui.base.SlidingActivity;
import com.timeline.vpn.R;

public class NormalActivity extends SlidingActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_activity_normal);
    }
    protected boolean enableSliding() {
        return true;
    }
}
