package com.timeline.vpn.ui.maintab;


import android.support.v4.app.Fragment;

import com.timeline.vpn.ui.base.features.TabBaseAdsFragment;
import com.timeline.vpn.ui.fragment.RecommendVipFragment;

/**
 * Created by themass on 2015/9/1.
 */
public class TabVipFragment extends TabBaseAdsFragment {
    @Override
    protected Fragment getTabHeaderView() {
        return null;
    }

    @Override
    protected Fragment getTabBodyView() {
        return new RecommendVipFragment();
    }
}
