package com.openapi.feature.content.sound;


import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.View;

import com.openapi.common.util.log.LogUtil;
import com.openapi.ads.base.AdsContext;
import com.openapi.feature.content.R;
import com.openapi.core.legacyui.app.BaseFragmentActivity;
import com.openapi.feature.content.sound.SoundChannleBodyFragment;

/**
 * Created by openapi on 2015/9/1.
 */
public class SoundChannleActivity extends BaseFragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        setFabUpVisibility(View.VISIBLE);
        Fragment fragment = null;
        try {
            fragment = SoundChannleBodyFragment.class.getDeclaredConstructor().newInstance();
            fragment.setArguments(getIntent().getExtras());
        } catch (Exception e) {
            LogUtil.e(e);
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)
                .commitAllowingStateLoss();
        setToolbarTitle(R.string.sound, true);
    }

    @Override
    public void setupView() {
        super.setupView();
        AdsContext.showRand(this, AdsContext.Categrey.CATEGREY_VPN2);
    }

    public boolean needShow() {
        return true;
    }

    protected boolean enableSliding() {
        return true;
    }
}
