package com.qq.myapp.ui.feedback;

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

import com.qq.common.ui.base.BaseFragment;
import com.qq.common.util.LogUtil;
import com.qq.yewu.ads.base.AdsContext;
import com.qq.myapp.bean.vo.FeedbackCateVo;
import com.qq.myapp.bean.vo.InfoListVo;
import com.qq.myapp.data.BaseService;
import com.qq.myapp.ui.base.CommonFragmentActivity;
import com.qq.myapp.ui.base.features.LoadableFragment;
import com.qq.ks.free1.R;
import com.viewpagerindicator.TabPageIndicator;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;

/**
 * Created by dengt on 2016/8/12.
 */
public class FeedbackChooseFragment extends LoadableFragment<InfoListVo<FeedbackCateVo>> {
    @Nullable
    @BindView(R.id.indicator)
    TabPageIndicator indicator;
    @Nullable
    @BindView(R.id.pager)
    ViewPager viewPager;
    InfoListVo<FeedbackCateVo> vo = new InfoListVo<FeedbackCateVo>();
    FragmentPagerAdapter adapter;
    public static void startFragment(Context context) {
        Intent intent = new Intent(context, FeedbackFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, FeedbackChooseFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, getFragmentTitle());
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, false);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN2);
        context.startActivity(intent);
    }

    public static int getFragmentTitle() {
        return R.string.feed_back;
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_feedback_fragment, parent);
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
    protected void onDataLoaded(InfoListVo<FeedbackCateVo> data) {
        vo = data;
        indicator.notifyDataSetChanged();
        adapter.notifyDataSetChanged();
    }

    @Override
    protected InfoListVo<FeedbackCateVo> loadData(Context context) throws Exception {
        InfoListVo<FeedbackCateVo> list = new InfoListVo<FeedbackCateVo>();
        list.hasMore=false;
        list.pageNum=1;
        list.total=2;
        List<FeedbackCateVo> cateVoList = new ArrayList<>();
        FeedbackCateVo wannt = new FeedbackCateVo(getString(R.string.iwanna_title),IWannaFragment.class);
        cateVoList.add(wannt);
        FeedbackCateVo feed = new FeedbackCateVo(getString(R.string.money_back_title),FeedbackFragment.class);
        cateVoList.add(feed);
        list.voList = cateVoList;
        return list;
    }

    @Override
    public void onDestroyView() {
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
            Class<? extends BaseFragment> clasz =  vo.voList.get(position).clasz;
            BaseFragment fragment = null;
            try {
                fragment = clasz.newInstance();
            }catch (Exception e){
                LogUtil.e(e);
            }
            return fragment;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return vo.voList.get(position).name;
        }

        @Override
        public int getCount() {
            return vo.voList.size();
        }
    }
}
