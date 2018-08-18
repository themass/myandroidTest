package com.timeline.vpn.ui.maintab.body;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sspacee.yewu.um.MobAgent;
import com.timeline.myapp.bean.vo.RecommendVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.ui.fragment.RecommendFragment;
import com.timeline.myapp.ui.fragment.VideoChannelListFragment;
import com.timeline.vpn.R;

/**
 * Created by themass on 2015/9/1.
 */
public class RecommendMovieFragment extends RecommendFragment {
    private static final String INDEX_TAG = "MovieRecommend_tag";
    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_index_fragment, parent);
    }
    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_RECOMMEND_MOVIE_URL,start);
    }

    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    public int getSpanCount() {
        return 3;
    }

    @Override
    public boolean getShowEdit() {
        return false;
    }
    public void onCustomerItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if(!checkUserLevel(vo.type)){
            return;
        }
        VideoChannelListFragment.startFragment(getActivity(), vo);
        MobAgent.onEventRecommondChannel(getActivity(), vo.title);
    }
}
