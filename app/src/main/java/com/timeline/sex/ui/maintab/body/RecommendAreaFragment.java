package com.timeline.sex.ui.maintab.body;


import android.view.View;

import com.timeline.sex.constant.Constants;
import com.timeline.sex.ui.fragment.RecommendFragment;

/**
 * Created by themass on 2015/9/1.
 */
public class RecommendAreaFragment extends RecommendFragment {
    private static final String INDEX_TAG = "Vip_tag";

    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_RECOMMEND_AREA_URL,start);
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
