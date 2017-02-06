package com.timeline.vpn.ui.base.app;

import com.timeline.vpn.data.MobAgent;
import com.timeline.vpn.ui.base.features.BaseBannerAdsActivity;

/**
 * Created by themass on 2016/8/18.
 */
public class BaseFragmentActivity extends BaseBannerAdsActivity {
    @Override
    public void onPause() {
        super.onPause();
        MobAgent.onPauseForFragmentActiviy(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        MobAgent.onResumeForFragmentActiviy(this);
    }
}
