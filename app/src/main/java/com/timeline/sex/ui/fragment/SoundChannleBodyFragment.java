package com.timeline.sex.ui.fragment;


import android.view.View;

import com.sspacee.yewu.um.MobAgent;
import com.timeline.sex.bean.vo.RecommendVo;
import com.timeline.sex.constant.Constants;

/**
 * Created by themass on 2015/9/1.
 */
public class SoundChannleBodyFragment extends RecommendFragment {
    private static final String INDEX_TAG = "SoundChannle_tag";

    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_SOUND_CHANNLE_URL, start);
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
