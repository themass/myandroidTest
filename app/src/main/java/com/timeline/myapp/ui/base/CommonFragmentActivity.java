package com.timeline.myapp.ui.base;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;

import com.qq.sexfree.R;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;

import com.timeline.myapp.ui.base.app.BaseFragmentActivity;
import com.timeline.myapp.ui.inte.FabOpListener;
import com.timeline.myapp.ui.inte.OnBackKeyDownListener;

import java.io.Serializable;

/**
 * Created by themass on 2016/9/5.
 */
public class CommonFragmentActivity extends BaseFragmentActivity implements FabOpListener.OnFabListener {
    public static final String FRAGMENT = "FRAGMENT";
    public static final String TITLE = "TITLE";
    public static final String PARAM = "PARAM";
    public static final String BANNER_ADS_SHOW = "BANNER_ADS_SHOW";
    public static final String BANNER_NEED_GONE = "BANNER_NEED_GONE";
    public static final String INTERSTITIAL_ADS_SHOW = "INTERSTITIAL_ADS_SHOW";
    public static final String BANNER_ADS_CATEGRY = "BANNER_ADS_CATEGRY";
    public static final String INTERSTITIAL_ADS_CATEGRYGRY = "INTERSTITIAL_ADS_CATEGRY";
    public static final String ADSSCROLL = "ADSSCROLL";
    public static final String SLIDINGCLOSE = "SLIDINGCLOSE";
    public static final String TOOLBAR_SHOW = "TOOLBAR_SHOW";
    public static final String FAB_SHOW="FAB_SHOW";
    private Boolean showAds = false;
    private boolean showInterstitialAds = false;
    private Boolean slidingClose = false;
    private Boolean toolbarShow = true;
    private AdsContext.Categrey bannerCategrey =  AdsContext.Categrey.CATEGREY_VPN1;
    private boolean needGonebanner = true;
    private Fragment fragment = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        slidingClose = getIntent().getBooleanExtra(SLIDINGCLOSE, true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        boolean scroll = getIntent().getBooleanExtra(ADSSCROLL, true);
        toolbarShow = getIntent().getBooleanExtra(TOOLBAR_SHOW, true);
        showInterstitialAds =getIntent().getBooleanExtra(INTERSTITIAL_ADS_SHOW, false);
        needGonebanner = getIntent().getBooleanExtra(BANNER_NEED_GONE, true);
        if (!scroll) {
            disableScrollBanner();
        }
        showToolbar(toolbarShow);
        Class f = (Class) getIntent().getSerializableExtra(FRAGMENT);
        showAds = getIntent().getBooleanExtra(BANNER_ADS_SHOW, false);
        Object o = getIntent().getSerializableExtra(BANNER_ADS_CATEGRY);
        if(o!=null){
            bannerCategrey = (AdsContext.Categrey)o;
        }
        setFabUpVisibility(getIntent().getIntExtra(FAB_SHOW,View.VISIBLE));
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
    }

    @Override
    public void setupView() {
        super.setupView();
        if(showInterstitialAds ){
            AdsContext.showRand(this);
        }
    }
    @Override
    protected boolean needGoneBanner(){
        return needGonebanner;
    }
    public boolean needShow() {
        return showAds;
    }

    @Override
    protected boolean enableSliding() {
        return slidingClose;
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
}
