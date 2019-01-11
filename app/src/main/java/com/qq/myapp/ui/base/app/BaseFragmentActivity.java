package com.qq.myapp.ui.base.app;

import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.um.MobAgent;

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
        super.onBackPressed();
    }
}
