package com.timeline.vpn.ui.base;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.R;
import com.timeline.vpn.ui.base.app.BaseFragmentActivity;
import com.timeline.vpn.ui.inte.FabOpListener;

/**
 * Created by themass on 2016/9/5.
 */
public class CommonFragmentActivity extends BaseFragmentActivity implements FabOpListener.OnFabListener {
    public static final String FRAGMENT = "FRAGMENT";
    public static final String TITLE = "TITLE";
    public static final String PARAM = "PARAM";
    public static final String ADS = "ADS";
    public static final String ADSSCROLL = "ADSSCROLL";
    private Boolean showAds=null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        boolean scroll = getIntent().getBooleanExtra(ADSSCROLL,true);
        if(!scroll){
            disableScrollBanner();
        }
        Class f = (Class) getIntent().getSerializableExtra(FRAGMENT);
        showAds = getIntent().getBooleanExtra(ADS,false);
        Integer title = getIntent().getIntExtra(TITLE, 0);
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
        if (title != 0) {
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
}
