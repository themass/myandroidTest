package com.timeline.vpn.ui.maintab;


import android.support.v4.app.Fragment;

import com.timeline.myapp.ui.base.BannerHeaderFragment;
import com.timeline.myapp.ui.base.features.TabBaseAdsFragment;

/**
 * Created by themass on 2015/9/1.
 */
public class TabLocalFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return new BannerHeaderFragment();
    }

    @Override
    protected Fragment getTabBodyView() {
//        LocationChooseFragment f = new LocationChooseFragment();
//        f.putSerializable(Boolean.FALSE);
        return null;
    }


}
