package com.timeline.vpn.ui.fragment;


import android.os.Bundle;
import android.view.View;

import com.sspacee.yewu.ads.adview.AdsAdview;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.UserLoginUtil;

/**
 * Created by themass on 2015/9/1.
 */
public class SoundChannleBodyFragment extends RecommendFragment {
    private static final String INDEX_TAG = "SoundChannle_tag";

    @Override
    public String getUrl(int start) {
        return Constants.getPage_URL(Constants.API_SOUND_CHANNLE_URL, start);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        if (!UserLoginUtil.isVIP2())
            AdsAdview.interstitialAds(getActivity(), null);
    }

    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    public void onItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        SoundItemsFragment.startFragment(getActivity(), vo);
        MobAgent.onEventRecommondChannel(getActivity(), vo.title);
    }

    @Override
    public int getSpanCount() {
        return 3;
    }

    @Override
    public boolean getShowEdit() {
        return false;
    }

}
