package com.timeline.vpn.ui.maintab;


import android.support.v4.app.Fragment;

import com.timeline.myapp.ui.base.BannerHeaderFragment;
import com.timeline.myapp.ui.base.features.TabBaseAdsFragment;
import com.timeline.vpn.ui.maintab.body.RecommendVipFragment;

/**
 * Created by themass on 2015/9/1.
 */
public class TabVipFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return new BannerHeaderFragment();
    }

    @Override
    protected Fragment getTabBodyView() {
        return new RecommendVipFragment();
    }


}
