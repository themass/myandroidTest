package com.timeline.vpn.ui.maintab;


import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.view.View;

import com.timeline.vpn.R;

import butterknife.Bind;

/**
 * Created by gqli on 2015/9/1.
 */
public class TabNewsFragment extends TabBaseAdsFragment {
    @Bind(R.id.tab_layout)
    TabLayout tabLayout;
    @Bind(R.id.vp_container)
    ViewPager viewPager;

    @Override
    protected int getTabBodyViewId() {
        return R.layout.common_page_view;
    }

    @Override
    protected int getTabHeaderViewId() {
        return R.layout.tab_energy_header;
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        fabUp.setImageResource(R.drawable.fab_xiaochuan2);
    }
}
