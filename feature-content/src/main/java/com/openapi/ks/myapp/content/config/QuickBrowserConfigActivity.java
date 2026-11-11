package com.openapi.ks.myapp.content.config;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.openapi.core.CommonConstants;
import com.openapi.core.legacyui.app.LogActivity;
import com.openapi.common.util.android.PackageUtils;
import com.openapi.core.ads.yewu.ads.base.AdsContext;
import com.openapi.feature.content.R;
import com.openapi.core.config.Constants;
import com.openapi.core.account.UserLoginUtil;
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
        if (UserLoginUtil.isVIP() || !adsPopNeed) {
            finishActivity();
        }
    }


    private void finishActivity() {
        finish();
    }
}
