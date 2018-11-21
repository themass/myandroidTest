package com.timeline.sexfree1.ui.maintab.body;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.qq.sexfree.R;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.ui.fragment.RecommendFragment;


/**
 * Created by themass on 2015/9/1.
 */
public class RecommendListFragment extends RecommendFragment {
    private static final String INDEX_TAG = "Recommend_tag";
    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_index_fragment, parent);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
    }
    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_RECOMMEND_URL,start);
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
}
