package chat.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;

import com.livekit.voiceassistant.LivekitVoiceAgentFragment;
import com.openai.ads.yewu.ads.base.AdsContext;
import com.openai.commonutil.EventBusUtil;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.PermissionHelper;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.ui.base.app.BaseFragmentActivity;
import com.openapi.ks.myapp.ui.inte.FabOpListener;
import com.openapi.ks.myapp.ui.inte.OnBackKeyDownListener;

import java.io.Serializable;

import io.livekit.android.annotations.Beta;

/**
 * Created by openapi on 2016/9/5.
 */
public class PhoneChatFragmentActivity extends BaseFragmentActivity implements FabOpListener.OnFabListener {
    public static final String FRAGMENT = "FRAGMENT";
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
    public static final String URL = "URL";
    public static final String TOKEN = "TOKEN";
    private Boolean showAds = false;
    private boolean showInterstitialAds = false;
    private Boolean slidingClose = false;
    private Boolean toolbarShow = true;
    private Boolean needFabup = true;
    private AdsContext.Categrey bannerCategrey = AdsContext.Categrey.CATEGREY_VPN1;
    private boolean needGonebanner = true;
    private Fragment fragment = null;
    private PermissionHelper mPermissionHelper;
    @OptIn(markerClass = Beta.class)
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
        Class f = (Class) getIntent().getSerializableExtra(FRAGMENT);
        showAds = getIntent().getBooleanExtra(BANNER_ADS_SHOW, false);
        needFabup = getIntent().getBooleanExtra(FABUP_SHOW, true);
        Object o = getIntent().getSerializableExtra(BANNER_ADS_CATEGRY);
        if (o != null) {
            bannerCategrey = (AdsContext.Categrey) o;
        }
        if (needFabup) {
            setFabUpVisibility(View.VISIBLE);
        }
        String title = null;
        Serializable name = getIntent().getSerializableExtra(TITLE);
        if (name instanceof String) {
            title = (String) name;
        } else if (name instanceof Integer) {
            title = getString((Integer) name);
        }
        mPermissionHelper = new PermissionHelper(this);
        mPermissionHelper.checkNeedPermissions();
        try {
            fragment =  LivekitVoiceAgentFragment.Companion.newInstance( getIntent().getStringExtra(URL), getIntent().getStringExtra(TOKEN));
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
    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults != null) {
            for (int ret : grantResults) {
                if (ret != PackageManager.PERMISSION_GRANTED) {
                    finish();
                }
            }
        }
        mPermissionHelper.checkNeedPermissions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mPermissionHelper.checkNeedPermissions();
    }
}
