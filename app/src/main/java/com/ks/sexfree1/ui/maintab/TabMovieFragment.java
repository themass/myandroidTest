package com.ks.sexfree1.ui.maintab;


import androidx.fragment.app.Fragment;

import com.sspacee.yewu.ads.base.AdsContext;
import com.ks.myapp.ui.base.BannerHeaderFragment;
import com.ks.myapp.ui.base.features.TabBaseAdsFragment;
import com.ks.sexfree1.ui.maintab.body.RecommendMovieFragment;

/**
 * Created by themass on 2015/9/1.
 */
public class TabMovieFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return BannerHeaderFragment.getNewInstans(AdsContext.Categrey.CATEGREY_VPN1);
    }

    @Override
    protected Fragment getTabBodyView() {
        return new RecommendMovieFragment();
    }
}
