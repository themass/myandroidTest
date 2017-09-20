package com.timeline.vpn.ui.base.app;

import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.ui.base.features.BaseBannerAdsActivity;

import net.youmi.android.nm.sp.SpotManager;

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
    @Override
    public void onBackPressed() {
        // 点击后退关闭轮播插屏广告
        LogUtil.i("onBackPressed");
        LogUtil.i("onBackPressed ; youmishou="+ SpotManager.getInstance(this).isSlideableSpotShowing());
        if (SpotManager.getInstance(this).isSlideableSpotShowing()) {
            SpotManager.getInstance(this).hideSlideableSpot();
        } else {
            super.onBackPressed();
        }
    }
}
