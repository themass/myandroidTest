package com.openapi.ks.myapp.ui.base.app;

import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.yewu.um.MobAgent;

/**
 * Created by openapi on 2016/8/18.
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
        super.onBackPressed();
    }
}
