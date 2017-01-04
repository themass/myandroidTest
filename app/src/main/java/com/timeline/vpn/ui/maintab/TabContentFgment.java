package com.timeline.vpn.ui.maintab;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.View;

import com.romainpiel.shimmer.Shimmer;
import com.romainpiel.shimmer.ShimmerTextView;
import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.ads.adview.NativeAdsAdapter;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.base.BaseBannerAdsFragemnt;

import butterknife.Bind;

/**
 * Created by themass on 2016/3/31.
 */
public class TabContentFgment extends BaseBannerAdsFragemnt {
    @Bind(R.id.rv_ads)
    RecyclerView rvAds;
    @Bind(R.id.tv_ready)
    ShimmerTextView tvReady;
    NativeAdsAdapter.AdsAdapter adsAdapter;
    Shimmer shimmer;

    @Override
    protected int getTabHeaderViewId() {
        return TabAdsFragment.NULL_VIEW;
    }

    @Override
    protected int getTabContentViewId() {
        return R.layout.layout_vip_temp_view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL);
        rvAds.setLayoutManager(layoutManager);
        rvAds.setItemAnimator(new DefaultItemAnimator());
        adsAdapter = new NativeAdsAdapter.AdsAdapter(getActivity());
        rvAds.setAdapter(adsAdapter);
        shimmer = new Shimmer();
        shimmer.setDuration(Constants.VIP_SHIMMER_DURATION);
        shimmer.start(tvReady);
    }

    @Override
    public void showAds(Context context) {
        super.showAds(context);
        AdsAdview.nativeAds(getActivity(), mHandler, adsAdapter);
    }

    @Override
    public void hidenAds(Context context) {
        super.hidenAds(context);
        adsAdapter.removeData();
    }
}
