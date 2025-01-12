package com.openapi.ks.chatfree.ui.maintab.teach;


import androidx.fragment.app.Fragment;

import com.openai.ads.yewu.ads.base.AdsContext;
import com.openapi.ks.chatfree.ui.maintab.body.RecommendMovieFragment;
import com.openapi.ks.chatfree.ui.maintab.body.teach.RecommendEnglishFragment;
import com.openapi.ks.myapp.ui.base.BannerHeaderFragment;
import com.openapi.ks.myapp.ui.base.features.TabBaseAdsFragment;

/**
 * Created by openapi on 2015/9/1.
 */
public class TabEnglishFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return BannerHeaderFragment.getNewInstans(AdsContext.Categrey.CATEGREY_VPN1);
    }

    @Override
    protected Fragment getTabBodyView() {
        return new RecommendEnglishFragment();
    }
}
