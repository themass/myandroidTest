package com.timeline.vpn.ui.fragment;


import com.timeline.vpn.constant.Constants;

/**
 * Created by themass on 2015/9/1.
 */
public class RecommendVipFragment extends RecommendFragment {
    private static final String INDEX_TAG = "Vip_tag";

    @Override
    public String getUrl(int start) {
        return Constants.getVIP_URL(start);
    }

    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }
}
