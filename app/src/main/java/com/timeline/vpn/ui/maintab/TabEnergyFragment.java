package com.timeline.vpn.ui.maintab;


import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;

import com.timeline.vpn.R;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;

/**
 * Created by gqli on 2015/9/1.
 */
public class TabEnergyFragment extends TabBaseAdsFragment {
    @Bind(R.id.tab_layout)
    TabLayout tabLayout;
    @Bind(R.id.vp_container)
    ViewPager viewPager;

    @Override
    protected int getTabBodyViewId() {
        return R.layout.tab_energy_content;
    }

    @Override
    protected int getTabHeaderViewId() {
        return R.layout.tab_energy_header;
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        setupViewPager(viewPager);
        tabLayout.setupWithViewPager(viewPager);
    }

    private void setupViewPager(ViewPager viewPager) {
        MyAdapter adapter = new MyAdapter(getChildFragmentManager());
        adapter.addFragment(new TestFragment(), R.string.tab_power_positive);
        adapter.addFragment(new TestFragment(), R.string.tab_power_negative);
        adapter.addFragment(new TestFragment(), R.string.tab_power_vent);
        adapter.addFragment(new TestFragment(), R.string.tab_power_gossip);
        viewPager.setAdapter(adapter);
    }

    class MyAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragments = new ArrayList<>();
        private final List<String> mFragmentTitles = new ArrayList<>();

        public MyAdapter(FragmentManager fm) {
            super(fm);
        }

        public void addFragment(Fragment fragment, int titleId) {
            mFragments.add(fragment);
            mFragmentTitles.add(getResources().getString(titleId));
        }

        @Override
        public Fragment getItem(int position) {
            return mFragments.get(position);
        }

        @Override
        public int getCount() {
            return mFragments.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitles.get(position);
        }
    }
}
