package com.timeline.vpn.ui.config;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;

import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.base.features.BaseBannerAdsActivity;
import com.timeline.vpn.ui.fragment.TmpContentFragment;

import java.util.HashMap;

/**
 * Created by themass on 2016/3/17.
 */
public class BrowserConfigActivity extends BaseBannerAdsActivity {
    private boolean adsNeed = false;
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
            switch (msg.what){
                case Constants.ADS_NO_MSG:
                    adsNeed = true;
                    break;
                default:
                    adsNeed = false;
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
        getFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)
                .commit();
        setNavigationOut();
        adsNeed = getIntent().getBooleanExtra(Constants.ADS_SHOW_CONFIG, false);
        flBanner.setVisibility(View.GONE);
        Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(getIntent().getExtras().getString(Constants.URL)));
        startActivity(it);
    }

    @Override
    public boolean needShow(Context context) {
        return adsNeed || super.needShow(this);
    }
    @Override
    public void showAds(Context context){
        super.showAds(context);
        if (needShow(this)) {
            AdsAdview.interstitialAds(this, mHandler);
        }

    }
}
