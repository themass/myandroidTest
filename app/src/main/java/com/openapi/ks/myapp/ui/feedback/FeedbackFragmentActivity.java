package com.openapi.ks.myapp.ui.feedback;

import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.openai.commonutil.LogUtil;
import com.openai.ads.yewu.ads.base.AdsContext;
import com.openapi.ks.myapp.ui.base.app.BaseFragmentActivity;
import com.openapi.ks.myapp.ui.inte.FabOpListener;
import com.openapi.ks.myapp.ui.inte.OnBackKeyDownListener;
import com.openapi.ks.chat.R;

import java.io.Serializable;

/**
 * Created by openapi on 2016/9/5.
 */
public class FeedbackFragmentActivity extends BaseFragmentActivity implements FabOpListener.OnFabListener {
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
    private AdsContext.Categrey bannerCategrey = AdsContext.Categrey.CATEGREY_VPN2;
    private AdsContext.Categrey interCategrey = AdsContext.Categrey.CATEGREY_VPN2;
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
        // 使用现代Bundle API替代getSerializableExtra
        Bundle extras = getIntent().getExtras();
        Class<?> f = null;
        if (extras != null) {
            // 兼容性处理：API 33+ 使用新方法，否则使用旧方法
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                f = (Class<?>) extras.getSerializable(FRAGMENT, Serializable.class);
                Object o = extras.getSerializable(BANNER_ADS_CATEGRY, Serializable.class);
                if (o != null) {
                    bannerCategrey = (AdsContext.Categrey) o;
                }
                Object o2 = extras.getSerializable(INTERSTITIAL_ADS_CATEGRY, Serializable.class);
                if (o2 != null) {
                    interCategrey = (AdsContext.Categrey) o2;
                }
            } else {
                f = (Class<?>) extras.getSerializable(FRAGMENT);
                Object o = extras.getSerializable(BANNER_ADS_CATEGRY);
                if (o != null) {
                    bannerCategrey = (AdsContext.Categrey) o;
                }
                Object o2 = extras.getSerializable(INTERSTITIAL_ADS_CATEGRY);
                if (o2 != null) {
                    interCategrey = (AdsContext.Categrey) o2;
                }
            }
        }
        showAds = getIntent().getBooleanExtra(BANNER_ADS_SHOW, false);
        String title = null;
        Serializable name = null;
        if (extras != null) {
            // 兼容性处理：API 33+ 使用新方法，否则使用旧方法
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                name = extras.getSerializable(TITLE, Serializable.class);
            } else {
                name = (Serializable) extras.getSerializable(TITLE);
            }
        }
        if (name instanceof String) {
            title = (String) name;
        } else if (name instanceof Integer) {
            title = getString((Integer) name);
        }
        try {
            fragment = (Fragment) f.getDeclaredConstructor().newInstance();
            Serializable param = null;
            if (extras != null) {
                // 兼容性处理：API 33+ 使用新方法，否则使用旧方法
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    param = extras.getSerializable(PARAM, Serializable.class);
                } else {
                    param = (Serializable) extras.getSerializable(PARAM);
                }
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
        fabUp.setVisibility(View.GONE);
    }

    @Override
    protected boolean needGoneBanner() {
        return needGonebanner;
    }

    public boolean needShow() {
        if (showAds != null) {
            return showAds;
        }
        return false;
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
                // 使用现代方式处理返回键
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getOnBackPressedDispatcher().onBackPressed();
                } else {
                    super.onBackPressed();
                }
            }
        } else {
            // 使用现代方式处理返回键
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getOnBackPressedDispatcher().onBackPressed();
            } else {
                super.onBackPressed();
            }
        }
    }
}
