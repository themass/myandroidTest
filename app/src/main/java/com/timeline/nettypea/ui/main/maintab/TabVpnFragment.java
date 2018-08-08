package com.timeline.nettypea.ui.main.maintab;


import android.support.v4.app.Fragment;

import com.timeline.myapp.ui.base.features.TabBaseAdsFragment;
import com.timeline.myapp.ui.fragment.VpnStatusFragment;
import com.timeline.nettypea.ui.main.maintab.body.RecommendListFragment;

/**
 * Created by themass on 2015/9/1.
 */
public class TabVpnFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return new VpnStatusFragment();
    }

    @Override
    protected Fragment getTabBodyView() {
        return new RecommendListFragment();
    }
}
