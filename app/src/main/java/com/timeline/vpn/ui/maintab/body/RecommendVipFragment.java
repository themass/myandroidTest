package com.timeline.vpn.ui.maintab.body;


import android.view.View;

import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.ui.fragment.RecommendFragment;


/**
 * Created by themass on 2015/9/1.
 */
public class RecommendVipFragment extends RecommendFragment {
    private static final String INDEX_TAG = "Vip_tag";

    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_RECOMMEND_VIP_URL,start);
    }

    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    public void onItemClick(View v, int position) {
        //关于
        if (position == 0) {
            super.onItemClick(v, position);
            return;
        }
        super.onItemClick(v, position);
    }

    @Override
    public int getSpanCount() {
        return 2;
    }

    @Override
    public boolean getShowEdit() {
        return false;
    }
}