package com.timeline.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sspacee.common.util.SystemUtils;
import com.sspacee.yewu.ads.base.AdsContext;
import com.timeline.myapp.bean.vo.InfoListVo;
import com.timeline.myapp.bean.vo.VipLocationVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.BaseService;
import com.timeline.myapp.ui.base.CommonFragmentActivity;
import com.timeline.myapp.ui.base.LocationFragmentActivity;
import com.timeline.myapp.ui.base.features.LoadableFragment;
import com.timeline.nettypea.R;
import com.viewpagerindicator.TabPageIndicator;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class LocationVipChooseFragment extends LoadableFragment<InfoListVo<VipLocationVo>> {
    private static final String LOCATION_TAG = "location_tag";
    @Nullable
    @BindView(R.id.indicator)
    TabPageIndicator indicator;
    @Nullable
    @BindView(R.id.pager)
    ViewPager viewPager;
    InfoListVo<VipLocationVo> vo = new InfoListVo<VipLocationVo>();
    FragmentPagerAdapter adapter;
    public static void startFragment(Context context) {
        Intent intent = new Intent(context, LocationFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, LocationVipChooseFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, getFragmentTitle());
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, false);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN2);
        context.startActivity(intent);
    }

    public static int getFragmentTitle() {
        return R.string.location_choose_title;
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_location_vip_choose_fragment, parent);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        indexService = new BaseService();
        indexService.setup(getActivity());
        adapter = new TabPageIndicatorAdapter(getChildFragmentManager());
        viewPager.setAdapter(adapter);
        indicator.setViewPager(viewPager);

    }

    @Override
    protected void onDataLoaded(InfoListVo<VipLocationVo> data) {
        vo = data;
        indicator.notifyDataSetChanged();
        adapter.notifyDataSetChanged();
    }

    @Override
    protected InfoListVo<VipLocationVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getUrl(Constants.API_LOCATION_VIP_URL), VipLocationVo.class, LOCATION_TAG);
    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(LOCATION_TAG);
        super.onDestroyView();
    }

    /**
     * 定义ViewPager的适配器
     */
    class TabPageIndicatorAdapter extends FragmentPagerAdapter {
        public TabPageIndicatorAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            //新建一个Fragment来展示ViewPager item的内容，并传递参数
            LocationVipItemChooseFragment fragment = new LocationVipItemChooseFragment();
            fragment.putSerializable(vo.voList.get(position));
            return fragment;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (SystemUtils.isZH(getActivity())) {
                return vo.voList.get(position).name;
            }
            return vo.voList.get(position).ename;
        }

        @Override
        public int getCount() {
            return vo.voList.size();
        }
    }
}
