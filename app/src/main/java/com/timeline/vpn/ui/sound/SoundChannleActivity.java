package com.timeline.vpn.ui.sound;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;

import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.R;
import com.timeline.vpn.ui.base.app.BaseFragmentActivity;
import com.timeline.vpn.ui.fragment.SoundChannleBodyFragment;

/**
 * Created by themass on 2015/9/1.
 */
public class SoundChannleActivity extends BaseFragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);

        Fragment fragment = null;
        try {
            fragment = (Fragment) SoundChannleBodyFragment.class.newInstance();
            setFabUpVisibility(View.GONE);
        } catch (Exception e) {
            LogUtil.e(e);
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)
                .commitAllowingStateLoss();
        setToolbarTitle(R.string.sound,true);
    }

    @Override
    public boolean needShow(Context context) {
        return true;
    }
}
