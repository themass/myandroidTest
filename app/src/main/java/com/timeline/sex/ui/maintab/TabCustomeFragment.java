package com.timeline.sex.ui.maintab;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;

import com.timeline.sex.R;
import com.timeline.sex.ui.base.features.TabBaseAdsFragment;
import com.timeline.sex.ui.fragment.RecommendCustomeFragment;
import com.timeline.sex.ui.user.AddCustomeInfoActivity;


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
