package com.qq.myapp.ui.base;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import com.qq.kb.R;
import com.sspacee.common.ui.base.BaseFragment;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.qq.myapp.data.UserLoginUtil;

import com.qq.myapp.data.config.HindBannerEvent;

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
    private AdsContext.Categrey ca;
    private static String CATEGREY = "Categrey";
    public static BannerHeaderFragment getNewInstans(AdsContext.Categrey ca){
        BannerHeaderFragment fragment = new BannerHeaderFragment();
        fragment.putSerializable(ca);
        return fragment;
    }
    @Override
    protected int getRootViewId() {
        return R.layout.common_banner;
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        ca = (AdsContext.Categrey)getSerializable();
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
        AdsManager.getInstans().exitBannerAds(getActivity(), flBanner, ca);
        super.onDestroyView();

    }
    public void showAds() {
        if (needShow() && UserLoginUtil.showAds()) {
            flBanner.setVisibility(View.VISIBLE);
            AdsManager.getInstans().showBannerAds(getActivity(), flBanner,ca);
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
        return AdsContext.rateShow();
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
