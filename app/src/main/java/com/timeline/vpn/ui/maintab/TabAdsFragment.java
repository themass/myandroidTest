package com.timeline.vpn.ui.maintab;


import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.ads.adview.NativeAdsAdapter;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import butterknife.Bind;

/**
 * Created by gqli on 2015/9/1.
 */
public class TabAdsFragment extends TabBaseAdsFragment {
    @Bind(R.id.ll_ads_banner)
    LinearLayout llAdsBanner;
    @Bind(R.id.rv_ads)
    RecyclerView rvAds;
    NativeAdsAdapter.AdsAdapter adsAdapter;
    LayoutInflater inflater;

    @Override
    protected int getTabBodyViewId() {
        return R.layout.tab_ads_content;
    }

    @Override
    protected int getTabHeaderViewId() {
        return R.layout.tab_ads_header;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        final StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL);
        rvAds.setLayoutManager(layoutManager);
        rvAds.setItemAnimator(new DefaultItemAnimator());
        adsAdapter = new NativeAdsAdapter.AdsAdapter(getActivity());
        rvAds.setAdapter(adsAdapter);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fetchBannerAd();
        fetchAd();
    }

    private void fetchBannerAd() {
        AdsAdview.bannerAds(getActivity(), llAdsBanner, mHandler, Constants.ADS_ADVIEW_KEY);
    }

    public void fetchAd() {
        AdsAdview.nativeAds(getActivity(), mHandler, adsAdapter);
    }

    @Override
    public void onDestroyView() {
        if (llAdsBanner != null) {
            LogUtil.i("remove all views");
            llAdsBanner.removeAllViews();
        }
        super.onDestroyView();
    }
}
