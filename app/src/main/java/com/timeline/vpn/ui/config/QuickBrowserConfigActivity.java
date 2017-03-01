package com.timeline.vpn.ui.config;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.sspacee.common.ui.base.LogActivity;
import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.constant.Constants;

/**
 * Created by themass on 2016/3/17.
 */
public class QuickBrowserConfigActivity extends LogActivity {
    private volatile boolean inited = false;
    private volatile boolean adOk = false;
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-QuickBrowserConfigActivity-" + msg.what);
            switch (msg.what) {
                case Constants.ADS_READY_MSG:
                    adOk = true;
                    break;
                case Constants.ADS_NO_MSG:
                    finishActivity();
                    break;
                case Constants.ADS_DISMISS_MSG:
                case Constants.ADS_CLICK_MSG:
                    finishActivity();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        //去除title
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
//        //去掉Activity上面的状态栏
//        getWindow().setFlags(WindowManager.LayoutParams. FLAG_FULLSCREEN , WindowManager.LayoutParams. FLAG_FULLSCREEN);
        setContentView(R.layout.layout_space);
        boolean adsPopNeed = getIntent().getBooleanExtra(Constants.ADS_POP_SHOW_CONFIG, false);
        final Uri uri = Uri.parse(getIntent().getExtras().getString(Constants.URL));
        startActivityForResult(new Intent(Intent.ACTION_VIEW, uri), 0);
        if (!adsPopNeed) {
            finishActivity();
        }
        AdsAdview.interstitialAdsRequest(this, mHandler);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        LogUtil.i("onActivityResult ->finishActivity");
        if (adOk) {
            AdsAdview.interstitialAdsShow(this);
        } else {
            finishActivity();
        }
    }

    //    @Override
//    public void onResume() {
//        super.onResume();
//        LogUtil.i("inited="+inited);
//        if(inited){
//            moveTaskToBack(true);
//            finishActivity();
//        }else {
//            inited = true;
//        }
//    }
    private void finishActivity() {
        LogUtil.i("finishActivity");
        finish();
    }
}