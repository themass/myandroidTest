package com.timeline.vpn.ui.base;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.sspacee.common.ui.view.MyWebView;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PackageUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.yewu.ads.adview.AdsAdview;
import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.ui.base.app.BaseFragmentActivity;
import com.timeline.vpn.ui.base.features.BaseWebViewFragment;

import java.util.HashMap;

/**
 * Created by themass on 2016/3/17.
 */
public class WebViewActivity extends BaseFragmentActivity implements MyWebView.OnTouchRightSlide {
    BaseWebViewFragment webViewFragment;
    private boolean adsNeed = false;
    private boolean adsPopNeed = false;
    private String url;
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
            switch (msg.what) {
                case Constants.ADS_NO_MSG:
                    adsPopNeed = true;
                    break;
                default:
                    adsPopNeed = false;
            }
        }
    };

    public static void startWebViewActivity(Context context, String url, String title, boolean adsShow, boolean adsPopShow, HashMap<String, Object> param) {
        Intent intent = new Intent(context, WebViewActivity.class);
        intent.putExtra(Constants.URL, url);
        intent.putExtra(Constants.ADS_SHOW_CONFIG, adsShow);
        intent.putExtra(Constants.ADS_POP_SHOW_CONFIG, adsPopShow);
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
        adsDelayGone();
        adsNeed = getIntent().getBooleanExtra(Constants.ADS_SHOW_CONFIG, false);
        adsPopNeed = getIntent().getBooleanExtra(Constants.ADS_POP_SHOW_CONFIG, false);
    }

    @Override
    public boolean needShow(Context context) {
        return adsNeed || super.needShow(this);
    }

    @Override
    public void showAds(Context context) {
        super.showAds(context);
        if (!UserLoginUtil.isVIP() && adsPopNeed) {
            AdsAdview.interstitialAds(this, mHandler);
        }

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
        getMenuInflater().inflate(R.menu.browser_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_browser) {
            if(PackageUtils.hasBrowser(this)){
                Uri uri = Uri.parse(url);
                Intent it = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(it);
                finish();
            }else{
                Toast.makeText(this,R.string.no_browser,Toast.LENGTH_SHORT).show();
            }
            return true;
        }else if (id == R.id.menu_url) {
            SystemUtils.copy(this,url);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(newConfig.orientation==Configuration.ORIENTATION_LANDSCAPE){
            getToolbar().setVisibility(View.GONE);
        }else{
            getToolbar().setVisibility(View.VISIBLE);
        }
    }
}
