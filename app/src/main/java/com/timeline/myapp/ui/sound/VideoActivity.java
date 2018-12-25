package com.timeline.myapp.ui.sound;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;

import com.qq.sexfree.R;
import com.sspacee.common.util.LogUtil;

import com.timeline.myapp.ui.base.app.BaseFragmentActivity;
import com.timeline.myapp.ui.fragment.AutoVideoFragment;

import cn.jzvd.Jzvd;
import cn.jzvd.JzvdStd;


/**
 * Created by themass on 2015/9/1.
 */
public class VideoActivity extends BaseFragmentActivity {
    Fragment fragment = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        JzvdStd.FULLSCREEN_ORIENTATION=1;
        setContentView(R.layout.common_fragment);
        setFabUpVisibility(View.GONE);
        try {
            fragment = AutoVideoFragment.class.newInstance();

        } catch (Exception e) {
            LogUtil.e(e);
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)
                .commitAllowingStateLoss();
        setToolbarTitle(R.string.video, true);
    }
    @Override
    public void onBackPressed() {
        if (Jzvd.backPress()) {
            return;
        }
        super.onBackPressed();
    }
    @Override
    public boolean needShow() {
        return true;
    }

    protected boolean enableSliding() {
        return true;
    }
//    @Override
//    public void setupView() {
//        super.setupView();
//        if(AdsContext.rateShow()){
//            AdsManager.getInstans().showInterstitialAds(this, AdsContext.Categrey.CATEGREY_VPN1,false);
//        }
//    }
}
