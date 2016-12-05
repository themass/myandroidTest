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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        webViewFragment = new BaseWebViewFragment();
        getFragmentManager().beginTransaction()
                .add(R.id.fragment, webViewFragment)
                .commit();
        setNavigationOut();
        adsDelayGone();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtil.i("onKeyDown");
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webViewFragment==null||!webViewFragment.goBack()) {
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
