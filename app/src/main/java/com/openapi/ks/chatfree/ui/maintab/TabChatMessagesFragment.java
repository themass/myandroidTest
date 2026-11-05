package com.openapi.ks.chatfree.ui.maintab;

import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.openapi.core.ads.yewu.ads.base.AdsContext;
import com.openapi.ks.myapp.ui.base.BannerHeaderFragment;
import com.openapi.ks.myapp.ui.base.features.TabBaseAdsFragment;

public class TabChatMessagesFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return BannerHeaderFragment.getNewInstans(AdsContext.Categrey.CATEGREY_VPN3);
    }

    @Override
    protected Fragment getTabBodyView() {
        return null;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fabUp.setVisibility(View.GONE);
//        ((MainFragmentViewPage) getActivity()).showToolbar(false);
    }

}
