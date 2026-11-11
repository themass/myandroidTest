package com.openapi.core.legacyui.base;

import android.os.Bundle;
import android.os.Build;

import androidx.fragment.app.Fragment;

import android.view.View;

import com.openapi.common.util.log.LogUtil;
import com.openapi.ads.base.AdsContext;
import com.openapi.core.R;
import com.openapi.core.legacyui.app.BaseFragmentActivity;
import com.openapi.core.legacyui.inte.FabOpListener;
import com.openapi.core.legacyui.inte.OnBackKeyDownListener;

import java.io.Serializable;

/**
 * Created by openapi on 2016/9/5.
 */
public class CommonFragmentActivity extends BaseFragmentActivity implements FabOpListener.OnFabListener {
    public static final String FRAGMENT = "FRAGMENT";
    /** 与 FRAGMENT 二选一：用类名指定页面，供 :core 打开 feature 模块里的页面。 */
    public static final String FRAGMENT_NAME = "FRAGMENT_NAME";
    public static final String TITLE = "TITLE";
    public static final String PARAM = "PARAM";
    public static final String BANNER_ADS_SHOW = "BANNER_ADS_SHOW";
    public static final String FABUP_SHOW = "FABUP_SHOW";
    public static final String BANNER_NEED_GONE = "BANNER_NEED_GONE";
    public static final String INTERSTITIAL_ADS_SHOW = "INTERSTITIAL_ADS_SHOW";
    public static final String BANNER_ADS_CATEGRY = "BANNER_ADS_CATEGRY";
    public static final String INTERSTITIAL_ADS_CATEGRYGRY = "INTERSTITIAL_ADS_CATEGRY";
    public static final String ADSSCROLL = "ADSSCROLL";
    public static final String SLIDINGCLOSE = "SLIDINGCLOSE";
    public static final String TOOLBAR_SHOW = "TOOLBAR_SHOW";
    private Boolean showAds = false;
    private boolean showInterstitialAds = false;
    private Boolean slidingClose = false;
    private Boolean toolbarShow = true;
    private Boolean needFabup = true;
    private AdsContext.Categrey bannerCategrey = AdsContext.Categrey.CATEGREY_VPN1;
    private boolean needGonebanner = true;
    private Fragment fragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        slidingClose = getIntent().getBooleanExtra(SLIDINGCLOSE, true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        boolean scroll = getIntent().getBooleanExtra(ADSSCROLL, true);
        toolbarShow = getIntent().getBooleanExtra(TOOLBAR_SHOW, true);
        showInterstitialAds = getIntent().getBooleanExtra(INTERSTITIAL_ADS_SHOW, false);
        needGonebanner = getIntent().getBooleanExtra(BANNER_NEED_GONE, true);
        if (!scroll) {
            disableScrollBanner();
        }
        showToolbar(toolbarShow);
        // 兼容性处理：API 33+ 使用新方法，否则使用旧方法
        Class f;
        String fragmentName = getIntent().getStringExtra(FRAGMENT_NAME);
        if (fragmentName != null) {
            try {
                f = Class.forName(fragmentName);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Unknown fragment: " + fragmentName, e);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            f = getIntent().getSerializableExtra(FRAGMENT, Class.class);
        } else {
            f = (Class) getIntent().getSerializableExtra(FRAGMENT);
        }
        showAds = getIntent().getBooleanExtra(BANNER_ADS_SHOW, false);
        needFabup = getIntent().getBooleanExtra(FABUP_SHOW, true);
        Object o;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            o = getIntent().getSerializableExtra(BANNER_ADS_CATEGRY, Serializable.class);
        } else {
            o = getIntent().getSerializableExtra(BANNER_ADS_CATEGRY);
        }
        if (o != null) {
            bannerCategrey = (AdsContext.Categrey) o;
        }
        if (needFabup) {
            setFabUpVisibility(View.VISIBLE);
        }
        String title = null;
        Serializable name;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            name = getIntent().getSerializableExtra(TITLE, Serializable.class);
        } else {
            name = getIntent().getSerializableExtra(TITLE);
        }
        if (name instanceof String) {
            title = (String) name;
        } else if (name instanceof Integer) {
            title = getString((Integer) name);
        }
        try {
            fragment = (Fragment) f.getDeclaredConstructor().newInstance();
            Serializable param;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                param = getIntent().getSerializableExtra(PARAM, Serializable.class);
            } else {
                param = getIntent().getSerializableExtra(PARAM);
            }
            if (param != null) {
                Bundle bundle = new Bundle();
                bundle.putSerializable(PARAM, param);
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
        if (showInterstitialAds) {
            AdsContext.showRand(this);
        }
    }

    @Override
    protected boolean needGoneBanner() {
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
        if (fragment instanceof OnBackKeyDownListener) {
            boolean ret = ((OnBackKeyDownListener) fragment).onkeyBackDown();
            if (!ret) {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }

    }
}
