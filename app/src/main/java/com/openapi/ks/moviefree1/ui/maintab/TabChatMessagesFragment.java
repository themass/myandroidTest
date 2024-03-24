package com.openapi.ks.moviefree1.ui.maintab;

import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.ks.moviefree1.ui.main.MainFragmentViewPage;
import com.openapi.ks.myapp.ui.base.BannerHeaderFragment;
import com.openapi.ks.myapp.ui.base.features.TabBaseAdsFragment;

import chat.ui.ChatMessagesListFragment;

public class TabChatMessagesFragment extends TabBaseAdsFragment {

    ChatMessagesListFragment f = new ChatMessagesListFragment();

    @Override
    protected Fragment getTabHeaderView() {
        return BannerHeaderFragment.getNewInstans(AdsContext.Categrey.CATEGREY_VPN3);
    }

    @Override
    protected Fragment getTabBodyView() {
        return f;
    }
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fabUp.setVisibility(View.GONE);
//        ((MainFragmentViewPage) getActivity()).showToolbar(false);
    }

}
