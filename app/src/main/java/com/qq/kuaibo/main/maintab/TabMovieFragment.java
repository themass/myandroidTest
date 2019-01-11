package com.qq.kuaibo.main.maintab;


import android.support.v4.app.Fragment;

import com.sspacee.yewu.ads.base.AdsContext;
import com.qq.myapp.ui.base.BannerHeaderFragment;
import com.qq.myapp.ui.base.features.TabBaseAdsFragment;
import com.qq.kuaibo.main.maintab.body.RecommendMovieFragment;

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
