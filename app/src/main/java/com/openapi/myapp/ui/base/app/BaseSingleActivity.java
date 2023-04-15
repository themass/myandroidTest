package com.openapi.myapp.ui.base.app;

import com.openapi.yewu.um.MobAgent;

/**
 * Created by dengt on 2016/8/18.
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
