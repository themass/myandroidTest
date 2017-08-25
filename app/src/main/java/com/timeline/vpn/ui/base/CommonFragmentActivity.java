package com.timeline.vpn.ui.base;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.R;
import com.timeline.vpn.ui.base.app.BaseFragmentActivity;
import com.timeline.vpn.ui.inte.FabOpListener;

import java.io.Serializable;

/**
 * Created by themass on 2016/9/5.
 */
public class CommonFragmentActivity extends BaseFragmentActivity implements FabOpListener.OnFabListener {
    public static final String FRAGMENT = "FRAGMENT";
    public static final String TITLE = "TITLE";
    public static final String PARAM = "PARAM";
    public static final String ADS = "ADS";
    public static final String ADSSCROLL = "ADSSCROLL";
    public static final String SLIDINGCLOSE="SLIDINGCLOSE";
    public static final String TOOLBAR_SHOW = "TOOLBAR_SHOW";
    private Boolean showAds=null;
    private Boolean slidingClose=false;
    private Boolean toolbarShow = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        slidingClose = getIntent().getBooleanExtra(SLIDINGCLOSE,true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        boolean scroll = getIntent().getBooleanExtra(ADSSCROLL,true);
        toolbarShow = getIntent().getBooleanExtra(TOOLBAR_SHOW,true);
        if(!scroll){
            disableScrollBanner();
        }
        Class f = (Class) getIntent().getSerializableExtra(FRAGMENT);
        showAds = getIntent().getBooleanExtra(ADS,false);
        String title=null;
        Serializable name = getIntent().getSerializableExtra(TITLE);
        if(name instanceof  String){
            title=(String)name;
        }else if(name instanceof Integer){
            title = getString((Integer)name);
        }
        Fragment fragment = null;
        try {
            fragment = (Fragment) f.newInstance();
            if(getIntent().getSerializableExtra(PARAM)!=null) {
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
            setToolbarTitle(title,true);
        }
    }

    @Override
    public boolean needShow(Context context) {
        if(showAds!=null){
            return  showAds;
        }
        return super.needShow(context);
    }

    @Override
    protected boolean enableSliding() {
        return slidingClose;
    }
}
