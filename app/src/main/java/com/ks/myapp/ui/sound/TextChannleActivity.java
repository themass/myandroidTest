package com.ks.myapp.ui.sound;


import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.View;

import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.ks.sexfree1.R;
import com.ks.myapp.ui.base.app.BaseFragmentActivity;
import com.ks.myapp.ui.fragment.body.TextChannleBodyFragment;

/**
 * Created by themass on 2015/9/1.
 */
public class TextChannleActivity extends BaseFragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        setFabUpVisibility(View.VISIBLE);
        Fragment fragment = null;
        try {
            fragment = TextChannleBodyFragment.class.newInstance();
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
