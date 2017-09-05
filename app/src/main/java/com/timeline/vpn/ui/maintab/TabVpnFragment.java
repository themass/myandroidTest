package com.timeline.vpn.ui.maintab;


import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.timeline.vpn.ui.base.features.TabBaseAdsFragment;
import com.timeline.vpn.ui.fragment.RecommendListFragment;
import com.timeline.vpn.ui.fragment.VpnStatusFragment;

/**
 * Created by themass on 2015/9/1.
 */
public class TabVpnFragment extends TabBaseAdsFragment {

    private boolean isFirst = false;

    @Override
    protected Fragment getTabHeaderView() {

        return new VpnStatusFragment();
    }

    @Override
    protected Fragment getTabBodyView() {
        return new RecommendListFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isFirst = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFirst) {
            isFirst = false;
//            if(!UserLoginUtil.isVIP()) {
//                next();
//            }
        }
    }
}
