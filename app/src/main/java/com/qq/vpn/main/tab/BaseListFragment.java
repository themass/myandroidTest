package com.qq.vpn.main.tab;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.qq.Constants;
import com.qq.vpn.ui.fragment.RecommendFragment;
import com.qq.fq3.R;

/**
 * Created by dengt on 2015/9/1.
 */
public class BaseListFragment extends RecommendFragment {
    private static final String INDEX_TAG = "Recommend_tag";
    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_mypullview, parent);
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

}
