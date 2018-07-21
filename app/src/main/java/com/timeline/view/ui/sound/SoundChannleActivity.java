package com.timeline.view.ui.sound;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;

import com.sspacee.common.util.LogUtil;
import com.timeline.sexfree1.R;
import com.timeline.view.ui.base.app.BaseFragmentActivity;
import com.timeline.view.ui.fragment.body.SoundChannleBodyFragment;

/**
 * Created by themass on 2015/9/1.
 */
public class SoundChannleActivity extends BaseFragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        setFabUpVisibility(View.VISIBLE);
        Fragment fragment = null;
        try {
            fragment = SoundChannleBodyFragment.class.newInstance();
            fragment.setArguments(getIntent().getExtras());
        } catch (Exception e) {
            LogUtil.e(e);
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)
                .commitAllowingStateLoss();
        setToolbarTitle(R.string.sound, true);
    }

    public boolean needShow() {
        return true;
    }

    protected boolean enableSliding() {
        return true;
    }
}