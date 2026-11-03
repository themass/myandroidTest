package com.openapi.myapp.ui.base.app;

import com.openapi.common.util.LogUtil;

/**
 * Created by dengt on 2016/8/18.
 */
public class BaseFragmentActivity extends BaseBannerAdsActivity {
    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }
    @Override
    public void onBackPressed() {
        // 点击后退关闭轮播插屏广告
        LogUtil.i("onBackPressed");
        super.onBackPressed();
    }
}
