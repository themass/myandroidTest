package com.openapi.ks.moviefree1.ui.maintab;


import androidx.fragment.app.Fragment;

import com.openapi.ks.myapp.ui.base.BannerHeaderFragment;
import com.openapi.ks.myapp.ui.base.features.TabBaseAdsFragment;
import com.openapi.ks.moviefree1.ui.maintab.body.RecommendLocalFragment;
import com.openapi.commons.yewu.ads.base.AdsContext;

/**
 * Created by openapi on 2015/9/1.
 */
public class TabLocalFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return BannerHeaderFragment.getNewInstans(AdsContext.Categrey.CATEGREY_VPN3);
    }

    @Override
    protected Fragment getTabBodyView() {
        return new RecommendLocalFragment();
    }


}
