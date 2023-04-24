package com.openapi.ks.ui.maintab;


import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.View;

import com.openapi.common.util.EventBusUtil;
import com.openapi.myapp.data.config.PingEvent;
import com.openapi.myapp.ui.base.features.TabBaseAdsFragment;
import com.openapi.myapp.ui.user.AddCustomeInfoActivity;
import com.openapi.ks.free1.R;
import com.openapi.ks.ui.maintab.body.RecommendCustomeFragment;


/**
 * Created by dengt on 2015/9/1.
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
        fabUp.setVisibility(View.VISIBLE);
        fabUp.setImageResource(R.drawable.add_option);
    }

    @Override
    public void onClickFab(View view) {
        AddCustomeInfoActivity.startActivity(getActivity(), null);
    }

}
