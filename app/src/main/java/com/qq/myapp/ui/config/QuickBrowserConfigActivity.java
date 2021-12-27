package com.qq.myapp.ui.config;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.qq.common.ui.base.LogActivity;
import com.qq.common.util.PackageUtils;
import com.qq.yewu.ads.base.AdsContext;
import com.qq.ks1.R;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.UserLoginUtil;
import com.qq.myapp.ui.base.WebViewActivity;

/**
 * Created by dengt on 2016/3/17.
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
           AdsContext.showRand(this,AdsContext.getNext());
        } else {
            finishActivity();
        }
    }

    private void finishActivity() {
        finish();
    }
}
