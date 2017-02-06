package com.timeline.vpn.ui.config;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.base.app.BaseFragmentActivity;
import com.timeline.vpn.ui.fragment.TmpContentFragment;

import java.util.HashMap;

/**
 * Created by themass on 2016/3/17.
 */
public class BrowserConfigActivity extends BaseFragmentActivity {
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
        TmpContentFragment fragment = new TmpContentFragment();
        HashMap<String,Object> param = new HashMap<>();
        param.put(Constants.TITLE,getString(R.string.titile_browser));
        fragment.putSerializable(param);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)
                .commit();
        setNavigationOut();
        adsNeed = getIntent().getBooleanExtra(Constants.ADS_SHOW_CONFIG, false);
        adsPopNeed = getIntent().getBooleanExtra(Constants.ADS_POP_SHOW_CONFIG, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(getIntent().getExtras().getString(Constants.URL)));
        startActivityForResult(it,0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        finish();
    }

    @Override
    public boolean needShow(Context context) {
        return adsNeed || super.needShow(this);
    }
    @Override
    public void showAds(Context context){
        super.showAds(context);
        if (adsPopNeed) {
            AdsAdview.interstitialAds(this, mHandler);
        }
    }
}
