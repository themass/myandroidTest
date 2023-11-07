package com.openapi.ks.moviefree1.ui.maintab.body;


import android.view.View;

import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.ui.fragment.RecommendFragment;

/**
 * Created by openapi on 2015/9/1.
 */
public class RecommendAreaFragment extends RecommendFragment {
    private static final String INDEX_TAG = "area_tag";

    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_RECOMMEND_AREA_URL,start);
    }

    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    public void onCustomerItemClick(View v, int position) {
        //关于
        if (position == 0) {
            super.onCustomerItemClick(v, position);
            return;
        }
        super.onCustomerItemClick(v, position);
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