package com.ks.sexfree1.ui.maintab;


import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.View;

import com.ks.myapp.ui.base.features.TabBaseAdsFragment;
import com.ks.myapp.ui.user.AddCustomeInfoActivity;
import com.ks.sexfree1.R;
import com.ks.sexfree1.ui.maintab.body.RecommendCustomeFragment;


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
