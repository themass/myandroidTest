package com.timeline.myapp.ui.sound;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;

import com.qq.sexfree.R;
import com.sspacee.common.util.LogUtil;

import com.timeline.myapp.ui.base.app.BaseFragmentActivity;
import com.timeline.myapp.ui.fragment.body.ImgChannleBodyFragment;

/**
 * Created by themass on 2015/9/1.
 */
public class TeleplayActivity extends BaseFragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        setFabUpVisibility(View.VISIBLE);
        Fragment fragment = null;
        try {
            fragment = ImgChannleBodyFragment.class.newInstance();
            Bundle bundle = new Bundle();
            fragment.setArguments(getIntent().getExtras());
        } catch (Exception e) {
            LogUtil.e(e);
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)
                .commitAllowingStateLoss();
        setToolbarTitle(R.string.tv, true);

    }

    public boolean needShow() {
        return true;
    }

    protected boolean enableSliding() {
        return true;
    }

}
