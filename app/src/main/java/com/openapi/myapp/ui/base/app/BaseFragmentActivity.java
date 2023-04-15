package com.openapi.myapp.ui.base.app;

import com.openapi.common.util.LogUtil;
import com.openapi.yewu.um.MobAgent;

/**
 * Created by dengt on 2016/8/18.
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
