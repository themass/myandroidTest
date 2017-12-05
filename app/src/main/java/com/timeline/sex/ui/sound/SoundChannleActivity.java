package com.timeline.sex.ui.sound;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;

import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.timeline.sex.R;
import com.timeline.sex.ui.base.app.BaseFragmentActivity;
import com.timeline.sex.ui.fragment.SoundChannleBodyFragment;

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
    }

    public boolean needShow() {
        return true;
    }

    protected boolean enableSliding() {
        return true;
    }
    @Override
    public void setupView() {
        super.setupView();
        if(AdsContext.rateShow()){
            AdsManager.getInstans().showInterstitialAds(this, AdsContext.Categrey.CATEGREY_VPN1,false);
        }
    }
}
