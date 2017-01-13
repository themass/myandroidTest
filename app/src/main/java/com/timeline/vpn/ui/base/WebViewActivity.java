package com.timeline.vpn.ui.base;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;

import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.view.MyWebView;

/**
 * Created by themass on 2016/3/17.
 */
public class WebViewActivity extends BaseBannerAdsActivity implements MyWebView.OnTouchRightSlide {
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
        }
    };
    BaseWebViewFragment webViewFragment;
    private boolean adsNeed = false;

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
        adsNeed = getIntent() == null ? false : getIntent().getBooleanExtra(Constants.ADS_SHOW_CONFIG, false);
    }

    @Override
    public boolean needShow(Context context) {
        return adsNeed || super.needShow(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (needShow(this)) {
            AdsAdview.interstitialAds(this, mHandler);
        }

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtil.i("onKeyDown");
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webViewFragment == null || !webViewFragment.goBack()) {
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
