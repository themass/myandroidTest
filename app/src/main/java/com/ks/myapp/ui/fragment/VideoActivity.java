package com.ks.myapp.ui.fragment;


import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.View;

import com.sspacee.common.util.LogUtil;
import com.ks.myapp.ui.base.app.BaseFragmentActivity;
import com.ks.sexfree1.R;

import cn.jzvd.JZVideoPlayer;
import cn.jzvd.JZVideoPlayerStandard;

/**
 * Created by themass on 2015/9/1.
 */
public class VideoActivity extends BaseFragmentActivity {
    Fragment fragment = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        JZVideoPlayerStandard.FULLSCREEN_ORIENTATION=1;
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
        if (JZVideoPlayer.backPress()) {
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
