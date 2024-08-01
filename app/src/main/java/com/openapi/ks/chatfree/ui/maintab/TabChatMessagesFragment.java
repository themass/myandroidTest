package com.openapi.ks.chatfree.ui.maintab;

import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.openai.ads.yewu.ads.base.AdsContext;
import com.openapi.ks.myapp.ui.base.BannerHeaderFragment;
import com.openapi.ks.myapp.ui.base.features.TabBaseAdsFragment;

import chat.ui.fragment.AsrChatMessagesFragment;

public class TabChatMessagesFragment extends TabBaseAdsFragment {

    AsrChatMessagesFragment f = new AsrChatMessagesFragment();

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
