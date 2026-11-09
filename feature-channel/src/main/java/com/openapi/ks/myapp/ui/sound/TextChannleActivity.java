package com.openapi.ks.myapp.ui.sound;


import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.View;

import com.openapi.core.util.LogUtil;
import com.openapi.core.ads.yewu.ads.base.AdsContext;
import com.openapi.feature.channel.R;
import com.openapi.ks.myapp.ui.base.app.BaseFragmentActivity;
import com.openapi.ks.myapp.ui.fragment.body.TextChannleBodyFragment;

/**
 * Created by openapi on 2015/9/1.
 */
public class TextChannleActivity extends BaseFragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        setFabUpVisibility(View.VISIBLE);
        Fragment fragment = null;
        try {
            fragment = TextChannleBodyFragment.class.getDeclaredConstructor().newInstance();
            fragment.setArguments(getIntent().getExtras());
        } catch (Exception e) {
            LogUtil.e(e);
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)
                .commitAllowingStateLoss();
        setToolbarTitle(R.string.text, true);
    }

    @Override
    public boolean needShow() {
        return true;
    }

    protected boolean enableSliding() {
        return true;
    }

    @Override
    public void setupView() {
        super.setupView();
        AdsContext.showRand(this);
    }
}
