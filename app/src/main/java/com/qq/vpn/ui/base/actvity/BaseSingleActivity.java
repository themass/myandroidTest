package com.qq.vpn.ui.base.actvity;

import com.qq.MobAgent;

/**
 * Created by dengt on 2016/8/18.
 */
public class BaseSingleActivity extends BaseFragmentActivity {
    @Override
    public void onPause() {
        super.onPause();
        MobAgent.onPause(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        MobAgent.onResume(this);
    }

    @Override
    public void setupView() {

    }
}
