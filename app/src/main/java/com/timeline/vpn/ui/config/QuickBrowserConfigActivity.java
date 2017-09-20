package com.timeline.vpn.ui.config;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.sspacee.common.ui.base.LogActivity;
import com.sspacee.common.util.PackageUtils;
import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.ui.base.WebViewActivity;

/**
 * Created by themass on 2016/3/17.
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
            startActivityForResult(new Intent(Intent.ACTION_VIEW, uri), 0);
        }
        if (UserLoginUtil.isVIP() || !adsPopNeed) {
            finishActivity();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (adOk) {
        } else {
            finishActivity();
        }
    }

    private void finishActivity() {
        finish();
    }
}
