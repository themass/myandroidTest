package com.timeline.vpn.ui.fragment;

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
import com.timeline.vpn.ui.base.features.TmpBaseBannerAdsFragemnt;

import java.util.HashMap;

import butterknife.Bind;

/**
 * Created by themass on 2016/3/31.
 */
public class TmpContentFragment extends TmpBaseBannerAdsFragemnt {
    @Bind(R.id.rv_ads)
    RecyclerView rvAds;
    @Bind(R.id.tv_ready)
    ShimmerTextView tvReady;
    NativeAdsAdapter.AdsAdapter adsAdapter;
    Shimmer shimmer;
    boolean adsNeed = true;

    @Override
    protected int getTabContentViewId() {
        return R.layout.layout_fragment_temp_view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        HashMap<String, Object> param = (HashMap) getSerializable();
        String title = (String) param.get(Constants.TITLE);
        Boolean show = (Boolean) param.get(Constants.ADSSHOW);
        adsNeed = (Boolean) show == null ? true : show;
        final StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL);
        rvAds.setLayoutManager(layoutManager);
        rvAds.setItemAnimator(new DefaultItemAnimator());
        adsAdapter = new NativeAdsAdapter.AdsAdapter(getActivity());
        rvAds.setAdapter(adsAdapter);
        tvReady.setText(title);
        shimmer = new Shimmer();
        shimmer.setDuration(Constants.VIP_SHIMMER_DURATION);
        shimmer.start(tvReady);
    }

    @Override
    public void showAds(Context context) {
        if (needShow(context)) {
            super.showAds(context);
            AdsAdview.nativeAds(getActivity(), mHandler, adsAdapter);
        }
    }

    @Override
    public void hidenAds(Context context) {
        super.hidenAds(context);
        adsAdapter.removeData();
    }

    @Override
    public boolean needShow(Context context) {
        return super.needShow(getActivity()) && adsNeed;
    }
}
