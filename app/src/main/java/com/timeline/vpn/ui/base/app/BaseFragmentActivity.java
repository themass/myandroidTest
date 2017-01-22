package com.timeline.vpn.ui.base.app;

import com.timeline.vpn.data.MobAgent;

/**
 * Created by themass on 2016/8/18.
 */
public class BaseFragmentActivity extends BaseToolBarActivity {
    @Override
    protected void onPause() {
        super.onPause();
        MobAgent.onPauseForFragment(this, getClass().getSimpleName());
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobAgent.onResumeForFragment(this, getClass().getSimpleName());
    }
}
