package com.openapi.ks.myapp.ui.config;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.openapi.commons.common.ui.base.LogActivity;
import com.openapi.commons.common.util.PackageUtils;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.ks.myapp.ui.base.WebViewActivity;

/**
 * Created by openapi on 2016/3/17.
 */
public class QuickBrowserConfigActivity extends LogActivity {
    private volatile boolean adOk = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
//        getWindow().setFlags(WindowManager.LayoutParams. FLAG_FULLSCREEN , WindowManager.LayoutParams. FLAG_FULLSCREEN);
        setContentView(R.layout.layout_space);
        boolean adsPopNeed = getIntent().getBooleanExtra(Constants.ADS_POP_SHOW_CONFIG, false);
        if (!PackageUtils.hasBrowser(this)) {
            Bundle bundle = getIntent().getExtras();
            WebViewActivity.startWebViewActivity(this, bundle.getString(Constants.URL), bundle.getString(Constants.TITLE), adsPopNeed, adsPopNeed, null);
        } else {
            final Uri uri = Uri.parse(getIntent().getExtras().getString(Constants.URL));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
//            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityForResult(intent, 0);
        }
        if (UserLoginUtil.isVIP() || !adsPopNeed) {
            finishActivity();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (adOk) {
            AdsContext.showRand(this);
        } else {
            finishActivity();
        }
    }

    private void finishActivity() {
        finish();
    }
}