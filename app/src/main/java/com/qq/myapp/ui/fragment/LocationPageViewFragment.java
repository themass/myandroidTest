package com.qq.myapp.ui.fragment;

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

import com.kyview.natives.NativeAdInfo;
import com.qq.common.util.CollectionUtils;
import com.qq.common.util.LogUtil;
import com.qq.common.util.SystemUtils;
import com.qq.myapp.bean.vo.InfoListVo;
import com.qq.myapp.bean.vo.RecommendVo;
import com.qq.myapp.bean.vo.VipLocationVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.ui.base.CommonFragmentActivity;
import com.qq.myapp.ui.base.LocationFragmentActivity;
import com.qq.myapp.ui.base.features.LoadableFragment;
import com.qq.fq2.R;
import com.qq.yewu.ads.base.AdsContext;
import com.qq.yewu.ads.base.AdsManager;
import com.qq.yewu.ads.base.NativeAdsReadyListener;
import com.viewpagerindicator.TabPageIndicator;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;

/**
 * Created by dengt on 2016/8/12.
 */
public class LocationPageViewFragment extends LoadableFragment<InfoListVo<VipLocationVo>> implements NativeAdsReadyListener {
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
        intent.putExtra(CommonFragmentActivity.FRAGMENT, LocationPageViewFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, getFragmentTitle());
        context.startActivity(intent);
    }

    public static int getFragmentTitle() {
        return R.string.location_choose_title;
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_location_page_fragment, parent);
    }
    public boolean onAdRecieved(List<NativeAdInfo> data) {
        if (!CollectionUtils.isEmpty(data)) {
            List<RecommendVo> list = new ArrayList<>();
            for (NativeAdInfo nativeAdInfo : data) {
                RecommendVo vo = new RecommendVo();
                vo.desc = nativeAdInfo.getDescription();
                vo.img = nativeAdInfo.getIconUrl();
                vo.title = nativeAdInfo.getTitle();
                vo.extra = nativeAdInfo;
                nativeAdInfo.onDisplay(new View(getActivity()));
                vo.dataType = RecommendVo.dataType_ADS;
                if (nativeAdInfo.getImageWidth() != 0)
                    vo.rate = nativeAdInfo.getImageHeight() / nativeAdInfo.getImageWidth();
                else {
                    vo.rate = 1f;
                }
                vo.showType = Constants.ShowType.Blur;
                list.add(vo);
            }
            LogUtil.i(list.toString());
        }
        return true;
    }
    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
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
            LocationItemFragment fragment = new LocationItemFragment();
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
