package com.timeline.view.ui.maintab;


import android.support.v4.app.Fragment;

import com.sspacee.yewu.ads.base.AdsContext;
import com.timeline.view.ui.base.BannerHeaderFragment;
import com.timeline.view.ui.base.features.TabBaseAdsFragment;
import com.timeline.view.ui.maintab.body.RecommendMovieFragment;

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
