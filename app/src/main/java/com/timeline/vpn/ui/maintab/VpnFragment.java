package com.timeline.vpn.ui.maintab;


import android.support.v4.app.Fragment;

import com.timeline.myapp.ui.base.features.TabBaseAdsFragment;
import com.timeline.myapp.ui.fragment.VpnRadFragment;
import com.timeline.vpn.ui.maintab.body.RecommendListFragment;

/**
 * Created by dengt on 2015/9/1.
 */
public class VpnFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return new VpnRadFragment();
    }

//    @Override
//    protected Fragment getTabBodyView() {
//        return null;
//    }
    @Override
    protected Fragment getTabBodyView() {
        return new RecommendListFragment();
    }
}
