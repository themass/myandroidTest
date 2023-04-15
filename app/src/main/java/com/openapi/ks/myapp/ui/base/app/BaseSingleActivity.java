package com.openapi.ks.myapp.ui.base.app;

import com.openapi.commons.yewu.um.MobAgent;

/**
 * Created by openapi on 2016/8/18.
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
