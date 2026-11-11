package com.openapi.core.legacyui.base;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.openapi.core.legacyui.app.LogActivity;
import com.openapi.common.view.view.MyWebView;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.android.PackageUtils;
import com.openapi.common.util.android.SystemUtils;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.core.R;
import com.openapi.core.config.Constants;
import com.openapi.core.legacyui.features.BaseWebViewFragment;

/**
 * Created by openapi on 2016/3/17.
 */
public class VideoWebViewActivity extends LogActivity implements MyWebView.OnTouchRightSlide {
    BaseWebViewFragment webViewFragment;
    private String url;

    public static void startWebViewActivity(Context context, String url, String title) {
        Intent intent = new Intent(context, VideoWebViewActivity.class);
        intent.putExtra(Constants.URL, url);
        intent.putExtra(Constants.TITLE, title);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        webViewFragment = new BaseWebViewFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, webViewFragment)
                .commit();
        String title = getIntent().getStringExtra(Constants.TITLE);
        url = getIntent().getStringExtra(Constants.URL);
    }

    @Override
    public void onTouchRight(int distans) {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        LogUtil.i("onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.browser_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_browser) {
            if (PackageUtils.hasBrowser(this)) {
                Uri uri = Uri.parse(url);
                Intent it = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(it);
                finish();
            } else {
                ToastUtil.showShort(com.openapi.common.R.string.no_browser);
            }
            return true;
        } else if (id == R.id.menu_url) {
            SystemUtils.copy(this, url);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
//        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
//            getToolbar().setVisibility(View.GONE);
//        } else {
//            getToolbar().setVisibility(View.VISIBLE);
//        }
    }
}
