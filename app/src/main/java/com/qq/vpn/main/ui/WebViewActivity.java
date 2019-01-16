package com.qq.vpn.main.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.qq.Constants;
import com.qq.ads.base.AdsContext;
import com.qq.ext.util.LogUtil;
import com.qq.ext.util.PackageUtils;
import com.qq.ext.util.SystemUtils;
import com.qq.ext.util.ToastUtil;
import com.qq.ext.view.MyWebView;
import com.qq.vpn.ui.base.actvity.BaseFragmentActivity;
import com.qq.network.R;

import java.util.HashMap;

/**
 * Created by dengt on 2016/3/17.
 */
public class WebViewActivity extends BaseFragmentActivity implements MyWebView.OnTouchRightSlide {
    private static final String TAG = "customeadd_tag";
    BaseWebViewFragment webViewFragment;
    private boolean adsNeed = true;
    private String url;
    public static void startWebViewActivity(Context context, String url, String title, boolean adsShow, boolean adsPopShow, HashMap<String, Object> param) {
        Intent intent = new Intent(context, WebViewActivity.class);
        intent.putExtra(Constants.URL, url);
        intent.putExtra(Constants.CONFIG_PARAM, param);
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
        setToolbarTitle(title);
        fabUp.setVisibility(View.GONE);
    }

    public boolean needShow() {
        return adsNeed;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtil.i("onKeyDown");
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webViewFragment == null || !webViewFragment.goBack()) {
                finish();
                return true;
            }
        }
        return true;
    }

    @Override
    public void onTouchRight(int distans) {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        LogUtil.i("onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.menu_browser, menu);
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
                ToastUtil.showShort(R.string.no_browser);
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
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            toolbar.setVisibility(View.GONE);
        } else {
            toolbar.setVisibility(View.VISIBLE);
        }
    }
    protected AdsContext.Categrey getBannerCategrey(){
        return AdsContext.Categrey.CATEGREY_VPN1;
    }
}
