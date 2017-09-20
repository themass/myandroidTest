package com.timeline.vpn.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import com.sspacee.common.ui.base.BaseFragment;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsController;
import com.sspacee.yewu.ads.base.BaseAdsController;
import com.timeline.vpn.R;
import com.timeline.vpn.data.config.HindBannerEvent;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/21.
 */
public class BannerHeaderFragment extends BaseFragment implements AdsController {
    @BindView(R.id.fl_banner)
    public ViewGroup flBanner;
    public boolean init = false;
    private AdsGoneTask task = new AdsGoneTask();
    protected Handler mHandler = new Handler();

    @Override
    protected int getRootViewId() {
        return R.layout.common_banner;
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        init = true;
        EventBusUtil.getEventBus().register(this);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        showAds(getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();
        showAds(getActivity());
    }

    @Override
    public void onPause() {
        super.onPause();
        hidenAds(getActivity());
    }

    @Override
    public void onDestroyView() {
        init = false;
        EventBusUtil.getEventBus().unregister(this);
        BaseAdsController.exitBanner(getActivity(),flBanner);
        super.onDestroyView();

    }

    @Override
    public void showAds(Context context) {
        if (needShow(context)) {
            if (getUserVisibleHint()) {
                flBanner.setVisibility(View.VISIBLE);
                BaseAdsController.bannerAds(getActivity(), flBanner);
            } else {
                hidenAds(context);
            }
        } else {
            if (flBanner != null)
                flBanner.setVisibility(View.GONE);
        }
    }

    @Override
    public void hidenAds(Context context) {
        if (flBanner != null) {
            LogUtil.i("remove all views");
            flBanner.removeAllViews();
            flBanner.setVisibility(View.GONE);
        }
        mHandler.removeCallbacks(task);
    }

    @Override
    public boolean needShow(Context context) {
        return init;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(HindBannerEvent event) {
        hidenAds(getActivity());
    }
    class AdsGoneTask implements Runnable {
        @Override
        public void run() {
            hidenAds(getActivity());
        }
    }
}
