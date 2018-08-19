package com.qq.vpn.main.tab;


import android.support.v4.app.Fragment;

import com.qq.vpn.ui.base.fragment.TabBaseAdsFragment;
import com.qq.vpn.ui.fragment.VpnRadFragment;

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
        return new BaseListFragment();
    }
}
