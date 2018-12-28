package com.qq.vpn.ui.maintab;


import android.support.v4.app.Fragment;

import com.qq.myapp.ui.base.BannerHeaderFragment;
import com.qq.myapp.ui.base.features.TabBaseAdsFragment;
import com.qq.myapp.ui.fragment.LocationPageViewFragment;
import com.qq.vpn.ui.maintab.body.RecommendListFragment;
import com.qq.yewu.ads.base.AdsContext;


/**
 * Created by dengt on 2015/9/1.
 */
public class TabLocalRecommFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return BannerHeaderFragment.getNewInstans(AdsContext.Categrey.CATEGREY_VPN1);
    }

    @Override
    protected Fragment getTabBodyView(){
        return new RecommendListFragment();
    }


}
