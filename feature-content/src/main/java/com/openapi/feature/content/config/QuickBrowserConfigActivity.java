package com.openapi.feature.content.config;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.openapi.core.config.CommonConstants;
import com.openapi.core.legacyui.app.LogActivity;
import com.openapi.common.util.android.PackageUtils;
import com.openapi.common.ads.base.AdsContext;
import com.openapi.feature.content.R;
import com.openapi.core.config.Constants;
import com.openapi.core.legacyui.base.WebViewActivity;

/**
 * Created by openapi on 2016/3/17.
 */
public class QuickBrowserConfigActivity extends LogActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
//        getWindow().setFlags(WindowManager.LayoutParams. FLAG_FULLSCREEN , WindowManager.LayoutParams. FLAG_FULLSCREEN);
        setContentView(R.layout.layout_space);
        boolean adsPopNeed = getIntent().getBooleanExtra(CommonConstants.ADS_POP_SHOW_CONFIG, false);
        if (!PackageUtils.hasBrowser(this)) {
            Bundle bundle = getIntent().getExtras();
            WebViewActivity.startWebViewActivity(this, bundle.getString(Constants.URL), bundle.getString(Constants.TITLE), adsPopNeed, adsPopNeed, null);
        } else {
            final Uri uri = Uri.parse(getIntent().getExtras().getString(Constants.URL));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
//            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        // 以前只有 VIP 或不需要弹广告时才 finish：非 VIP 用户会滞留在这个 2dp 透明页上——
        // LogActivity 并不会在这里弹插屏，本页等于空转，还没有任何返回入口。
        // 本页只是跳转分发器；弹广告的逻辑在 WebViewActivity 内部（它收到 adsPopShow 自己弹），
        // 所以无论什么身份，派发完一律关掉。
        finishActivity();
    }


    private void finishActivity() {
        finish();
    }
}
