package com.timeline.vpn.ui.base;

import com.timeline.vpn.data.MobAgent;

/**
 * Created by themass on 2016/8/18.
 */
public class BaseSingleActivity extends BaseToolBarActivity {
    @Override
    protected void onPause() {
        super.onPause();
        MobAgent.onPause(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobAgent.onResume(this);
    }
}
