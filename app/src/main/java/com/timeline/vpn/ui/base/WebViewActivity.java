package com.timeline.vpn.ui.base;

import android.os.Bundle;
import android.view.KeyEvent;

import com.timeline.vpn.R;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.ui.view.MyWebView;

/**
 * Created by gqli on 2016/3/17.
 */
public class WebViewActivity extends BaseBannerAdsActivity implements MyWebView.OnTouchRightSlide {
    BaseWebViewFragment webViewFragment;
    int BACK_MIN = 60;
    int FORWORD_MIN = -60;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.config_web_layout);
        webViewFragment = (BaseWebViewFragment) getFragmentManager().findFragmentById(R.id.base_webview);
        setNavigationOut();
        webViewFragment.setListener(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtil.i("onKeyDown");
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!webViewFragment.goBack()) {
                finish();
                return super.onKeyDown(keyCode, event);
            }
        }
        return false;
    }

    @Override
    public void onTouchRight(int distans) {
    }

}
