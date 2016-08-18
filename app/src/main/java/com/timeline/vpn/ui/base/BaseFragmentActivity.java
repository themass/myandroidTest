package com.timeline.vpn.ui.base;

import com.timeline.vpn.data.MobAgent;

/**
 * Created by gqli on 2016/8/18.
 */
public class BaseFragmentActivity extends BaseToolBarActivity {
    @Override
    protected void onPause() {
        super.onPause();
        MobAgent.onPauseForFragment(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobAgent.onResumeForFragment(this);
    }
}
