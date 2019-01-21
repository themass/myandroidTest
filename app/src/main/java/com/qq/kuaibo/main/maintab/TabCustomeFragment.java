package com.qq.kuaibo.main.maintab;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;

import com.qq.kb.R;
import com.qq.myapp.ui.base.features.TabBaseAdsFragment;
import com.qq.myapp.ui.user.AddCustomeInfoActivity;

import com.qq.kuaibo.main.maintab.body.RecommendCustomeFragment;


/**
 * Created by themass on 2015/9/1.
 */
public class TabCustomeFragment extends TabBaseAdsFragment {
    RecommendCustomeFragment f = new RecommendCustomeFragment();

    @Override
    protected Fragment getTabHeaderView() {
        return null;
    }

    @Override
    protected Fragment getTabBodyView() {
        return f;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fabUp.setImageResource(R.drawable.add_option);
    }

    @Override
    public void onClickFab(View view) {
        AddCustomeInfoActivity.startActivity(getActivity(), null);
    }
}
