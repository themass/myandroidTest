package com.timeline.vpn.ui.base;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;

import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.ui.base.app.BaseFragmentActivity;
import com.timeline.vpn.ui.base.features.BaseWebViewFragment;
import com.sspacee.common.ui.view.MyWebView;

import java.util.HashMap;

/**
 * Created by themass on 2016/3/17.
 */
public class WebViewActivity extends BaseFragmentActivity implements MyWebView.OnTouchRightSlide {
    BaseWebViewFragment webViewFragment;
    private boolean adsNeed = false;
    private boolean adsPopNeed = false;
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
            switch (msg.what){
                case Constants.ADS_NO_MSG:
                    adsPopNeed = true;
                    break;
                default:
                    adsPopNeed = false;
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        webViewFragment = new BaseWebViewFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, webViewFragment)
                .commit();
        String title = getIntent().getStringExtra(Constants.TITLE);
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
    public void showAds(Context context){
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
                return super.onKeyDown(keyCode, event);
            }
        }
        return false;
    }

    @Override
    public void onTouchRight(int distans) {

    }
    public static void startWebViewActivity(Context context, String url, String title, boolean adsShow,boolean adsPopShow, HashMap<String,Object> param){
        Intent intent = new Intent(context, WebViewActivity.class);
        intent.putExtra(Constants.URL, url);
        intent.putExtra(Constants.ADS_SHOW_CONFIG, adsShow);
        intent.putExtra(Constants.ADS_POP_SHOW_CONFIG, adsPopShow);
        intent.putExtra(Constants.CONFIG_PARAM, param);
        intent.putExtra(Constants.TITLE, title);
        context.startActivity(intent);
    }

}
