package com.openapi.ks.ui.maintab;


import androidx.fragment.app.Fragment;

import com.openapi.myapp.ui.base.features.TabBaseAdsFragment;
import com.openapi.myapp.ui.fragment.VpnRadFragment;
import com.openapi.ks.ui.maintab.body.RecommendListFragment;

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
