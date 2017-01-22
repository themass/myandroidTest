package com.timeline.vpn.ui.base;

import android.app.Fragment;
import android.os.Bundle;

import com.timeline.vpn.R;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.ui.base.features.BaseBannerAdsActivity;
import com.timeline.vpn.ui.inte.FabOpListener;

/**
 * Created by themass on 2016/9/5.
 */
public class CommonFragmentActivity extends BaseBannerAdsActivity implements FabOpListener.OnFabListener {
    public static final String FRAGMENT = "FRAGMENT";
    public static final String TITLE = "TITLE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);

        Class f = (Class) getIntent().getSerializableExtra(FRAGMENT);
        Integer title = getIntent().getIntExtra(TITLE, 0);
        Fragment fragment = null;
        try {
            fragment = (Fragment) f.newInstance();
            if (fragment instanceof FabOpListener.SetFabListener) {
                ((FabOpListener.SetFabListener) fragment).setFabUpListener(this);
            }
        } catch (Exception e) {
            LogUtil.e(e);
        }
        getFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)
                .commitAllowingStateLoss();
        if (title != 0) {
            setToolbarTitle(title);
        }
    }
}
