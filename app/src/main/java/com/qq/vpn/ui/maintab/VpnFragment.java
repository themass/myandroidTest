package com.qq.vpn.ui.maintab;


import android.support.v4.app.Fragment;

import com.qq.myapp.ui.base.features.TabBaseAdsFragment;
import com.qq.myapp.ui.fragment.LocationPageViewFragment;
import com.qq.myapp.ui.fragment.VpnRadFragment;
import com.qq.vpn.ui.maintab.body.RecommendListFragment;

/**
 * Created by dengt on 2015/9/1.
 */
public class VpnFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return new VpnRadFragment();
    }

    @Override
    protected Fragment getTabBodyView() {
            return new RecommendListFragment();
    }
}
