package com.timeline.vpn.ui.base;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import com.sspacee.common.ui.base.BaseFragment;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.timeline.vpn.R;
import com.timeline.vpn.data.config.HindBannerEvent;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/21.
 */
public class BannerHeaderFragment extends BaseFragment{
    @BindView(R.id.fl_banner)
    public ViewGroup flBanner;
    private AdsGoneTask task = new AdsGoneTask();
    protected Handler mHandler = new Handler();
    public static final String BANNER_ADS_CATEGRY = "BANNER_ADS_CATEGRY";

    @Override
    protected int getRootViewId() {
        return R.layout.common_banner;
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        EventBusUtil.getEventBus().register(this);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        showAds();
    }

    @Override
    public void onResume() {
        super.onResume();
        showAds();
    }

    @Override
    public void onPause() {
        super.onPause();
        hidenAds();
    }

    @Override
    public void onDestroyView() {
        EventBusUtil.getEventBus().unregister(this);
        AdsManager.getInstans().exitBannerAds(getActivity(), flBanner, AdsContext.Categrey.CATEGREY_VPN1);
        super.onDestroyView();

    }
    public void showAds() {
        if (needShow()) {
            flBanner.setVisibility(View.VISIBLE);
            AdsManager.getInstans().showBannerAds(getActivity(), flBanner,AdsContext.Categrey.CATEGREY_VPN1);
        } else {
            if (flBanner != null)
                flBanner.setVisibility(View.GONE);
        }
    }
    public void hidenAds() {
        if (flBanner != null) {
            LogUtil.i("remove all views");
            flBanner.removeAllViews();
            flBanner.setVisibility(View.GONE);
        }
        mHandler.removeCallbacks(task);
    }
    public boolean needShow() {
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(HindBannerEvent event) {
        hidenAds();
    }
    class AdsGoneTask implements Runnable {
        @Override
        public void run() {
            hidenAds();
        }
    }
}
