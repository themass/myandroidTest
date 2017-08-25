package com.timeline.vpn.ui.fragment;


import android.view.View;

import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;

/**
 * Created by themass on 2015/9/1.
 */
public class TextChannleBodyFragment extends RecommendFragment {
    private static final String INDEX_TAG = "text_tag";

    @Override
    public String getUrl(int start) {
        return Constants.getPage_URL(Constants.API_TEXT_CHANNLE_URL,start);
    }

    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    public void onItemClick(View v, int position) {
        RecommendVo vo = infoVo.voList.get(position);
        TextChannelListFragment.startFragment(getActivity(),vo);
        MobAgent.onEventRecommond(getActivity(), vo.title);
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
