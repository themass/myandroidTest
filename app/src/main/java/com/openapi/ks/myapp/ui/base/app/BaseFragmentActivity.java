package com.openapi.ks.myapp.ui.base.app;

import android.os.Build;
import com.openai.commonutil.LogUtil;
import com.openai.ads.yewu.um.MobAgent;

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
        // 使用现代API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackPressedDispatcher().onBackPressed();
        } else {
            super.onBackPressed();
        }
    }
}
