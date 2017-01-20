package com.timeline.vpn.ui.maintab;


import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;

import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.base.TmpContentFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import butterknife.Bind;

/**
 * Created by themass on 2015/9/1.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class TabVipFragment extends TabBaseAdsFragment {
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
        return R.layout.tab_vip_header;
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        setupViewPager(viewPager);
        tabLayout.setupWithViewPager(viewPager);
    }

    private void setupViewPager(ViewPager viewPager) {
        MyAdapter adapter = new MyAdapter(getChildFragmentManager());
        TmpContentFragment fragment = new TmpContentFragment();
        HashMap<String,Object> param = new HashMap<>();
        param.put(Constants.ADSSHOW,true);
        param.put(Constants.TITLE,getString(R.string.tab_vip_temp));
        fragment.putSerializable(param);
        adapter.addFragment(fragment, R.string.tab_tag_vip);
        viewPager.setAdapter(adapter);

//        adapter.addFragment(new TabContentFgment(), R.string.tab_power_vent);
//        adapter.addFragment(new TabContentFgment(), R.string.tab_power_gossip);
//        adapter.addFragment(new TabContentFgment(), R.string.tab_power_yellow);

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
