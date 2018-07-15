package com.timeline.view.ui.base.app;

import com.sspacee.yewu.um.MobAgent;

/**
 * Created by themass on 2016/8/18.
 */
public class BaseSingleActivity extends BaseBannerAdsActivity {
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
