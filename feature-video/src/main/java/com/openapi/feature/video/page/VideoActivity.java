package com.openapi.feature.video.page;


import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.View;

import com.openapi.common.util.log.LogUtil;
import com.openapi.feature.video.R;
import com.openapi.core.legacyui.app.BaseFragmentActivity;
import com.openapi.feature.video.list.GSYAutoVideoListFragment;
import com.shuyu.gsyvideoplayer.GSYVideoManager;

/**
 * Created by openapi on 2015/9/1.
 */
public class VideoActivity extends BaseFragmentActivity {
    Fragment fragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        setFabUpVisibility(View.GONE);
        try {
            fragment = GSYAutoVideoListFragment.class.getDeclaredConstructor().newInstance();

        } catch (Exception e) {
            LogUtil.e(e);
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)
                .commitAllowingStateLoss();
        setToolbarTitle(R.string.video, true);
    }

    @Override
    public void onBackPressed() {
        if (GSYVideoManager.backFromWindowFull(this)) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean needShow() {
        return true;
    }

    protected boolean enableSliding() {
        return true;
    }
}
