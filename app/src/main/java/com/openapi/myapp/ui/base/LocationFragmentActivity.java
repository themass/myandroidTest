package com.openapi.myapp.ui.base;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.View;

import com.openapi.common.util.EventBusUtil;
import com.openapi.common.util.LogUtil;
import com.openapi.myapp.data.config.PingEvent;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.myapp.ui.base.app.BaseFragmentActivity;
import com.openapi.myapp.ui.inte.FabOpListener;
import com.openapi.myapp.ui.inte.OnBackKeyDownListener;
import com.openapi.ks.free1.R;

import java.io.Serializable;

/**
 * Created by dengt on 2016/9/5.
 */
public class LocationFragmentActivity extends BaseFragmentActivity implements FabOpListener.OnFabListener {
    public static final String FRAGMENT = "FRAGMENT";
    public static final String TITLE = "TITLE";
    public static final String PARAM = "PARAM";
    public static final String BANNER_ADS_SHOW = "BANNER_ADS_SHOW";
    public static final String BANNER_NEED_GONE = "BANNER_NEED_GONE";
    public static final String INTERSTITIAL_ADS_SHOW = "INTERSTITIAL_ADS_SHOW";
    public static final String BANNER_ADS_CATEGRY = "BANNER_ADS_CATEGRY";
    public static final String INTERSTITIAL_ADS_CATEGRY = "INTERSTITIAL_ADS_CATEGRY";
    public static final String ADSSCROLL = "ADSSCROLL";
    public static final String SLIDINGCLOSE = "SLIDINGCLOSE";
    public static final String TOOLBAR_SHOW = "TOOLBAR_SHOW";
    private Boolean showAds = null;
    private boolean showInterstitialAds = false;
    private Boolean slidingClose = false;
    private Boolean toolbarShow = true;
    private AdsContext.Categrey bannerCategrey =  AdsContext.Categrey.CATEGREY_VPN1;
    private AdsContext.Categrey interCategrey =  AdsContext.Categrey.CATEGREY_VPN1;
    private Fragment fragment = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        slidingClose = getIntent().getBooleanExtra(SLIDINGCLOSE, true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        toolbarShow = getIntent().getBooleanExtra(TOOLBAR_SHOW, true);
        showInterstitialAds =getIntent().getBooleanExtra(INTERSTITIAL_ADS_SHOW, false);
        disableScrollBanner();
        showToolbar(toolbarShow);
        Class f = (Class) getIntent().getSerializableExtra(FRAGMENT);
        showAds = getIntent().getBooleanExtra(BANNER_ADS_SHOW, false);
        Object o = getIntent().getSerializableExtra(BANNER_ADS_CATEGRY);
        if(o!=null){
            bannerCategrey = (AdsContext.Categrey)o;
        }
        o = getIntent().getSerializableExtra(INTERSTITIAL_ADS_CATEGRY);
        if(o!=null){
            interCategrey = (AdsContext.Categrey)o;
        }
        AdsContext.showRand(this,AdsContext.getNext());
        String title = null;
        Serializable name = getIntent().getSerializableExtra(TITLE);
        if (name instanceof String) {
            title = (String) name;
        } else if (name instanceof Integer) {
            title = getString((Integer) name);
        }
        try {
            fragment = (Fragment) f.newInstance();
            if (getIntent().getSerializableExtra(PARAM) != null) {
                Bundle bundle = new Bundle();
                bundle.putSerializable(PARAM, getIntent().getSerializableExtra(PARAM));
                fragment.setArguments(bundle);
            }
            if (fragment instanceof FabOpListener.SetFabListener) {
                ((FabOpListener.SetFabListener) fragment).setFabUpListener(this);
            }
        } catch (Exception e) {
            LogUtil.e(e);
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)
                .commitAllowingStateLoss();
        if (title != null) {
            setToolbarTitle(title, true);
        }
        setFabUpVisibility(View.VISIBLE);
        fabUp.setImageResource(R.drawable.ic_ping_clear_b);
    }
    @Override
    protected boolean needPingView(){
        return false;
    }

    @Override
    protected boolean needGoneBanner(){
        return false;
    }
    public boolean needShow() {
        return false;
    }

    @Override
    protected boolean enableSliding() {
        return slidingClose;
    }

    @Override
    protected boolean showLoc() {
        return false;
    }

    @Override
    protected AdsContext.Categrey getBannerCategrey() {
        return bannerCategrey;
    }
    @Override
    public void onBackPressed() {
        if(fragment instanceof OnBackKeyDownListener){
            boolean ret = ((OnBackKeyDownListener)fragment).onkeyBackDown();
            if(!ret){
                super.onBackPressed();
            }
        }else{
            super.onBackPressed();
        }
    }
    @Override
    public void onClickFab(View view) {
//        super.onClickFab(view);
        EventBusUtil.getEventBus().post(new PingEvent());
    }
}
