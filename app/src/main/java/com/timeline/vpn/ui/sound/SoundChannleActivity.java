package com.timeline.vpn.ui.sound;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;

import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.BaseAdsController;
import com.timeline.vpn.R;
import com.timeline.vpn.data.UserLoginUtil;
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
        setFabUpVisibility(View.VISIBLE);
        Fragment fragment = null;
        try {
            fragment = SoundChannleBodyFragment.class.newInstance();

        } catch (Exception e) {
            LogUtil.e(e);
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)
                .commitAllowingStateLoss();
        setToolbarTitle(R.string.sound, true);
        if (!UserLoginUtil.isVIP2())
            BaseAdsController.interstitialAds(this, BaseAdsController.AdsFrom.YOUMI);
    }

    @Override
    public boolean needShow(Context context) {
        return true;
    }

    protected boolean enableSliding() {
        return true;
    }
    @Override
    protected BaseAdsController.AdsFrom getBannerAdsFrom() {
        return BaseAdsController.AdsFrom.YOUMI;
    }

}
