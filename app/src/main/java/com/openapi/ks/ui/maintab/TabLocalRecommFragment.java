package com.openapi.ks.ui.maintab;


import androidx.fragment.app.Fragment;

import com.openapi.myapp.ui.base.BannerHeaderFragment;
import com.openapi.myapp.ui.base.features.TabBaseAdsFragment;
import com.openapi.ks.ui.maintab.body.RecommendListFragment;
import com.openapi.yewu.ads.base.AdsContext;


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
