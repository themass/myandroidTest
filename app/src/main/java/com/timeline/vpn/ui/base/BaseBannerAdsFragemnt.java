package com.timeline.vpn.ui.base;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.ads.adview.AdsController;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.maintab.TabBaseFragment;

import butterknife.Bind;

/**
 * Created by themass on 2016/8/21.
 */
public abstract class BaseBannerAdsFragemnt extends TabBaseFragment implements AdsController {
    @Bind(R.id.fl_content)
    public ViewGroup flContent;
    @Bind(R.id.fab_up)
    public FloatingActionButton fabUp;
    @Bind(R.id.fl_banner)
    public ViewGroup flBanner;
    public boolean init = false;
    private AdsGoneTask task = new AdsGoneTask();
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
            mHandler.postDelayed(task, Constants.BANNER_ADS_GONE_LONG);
        }
    };

    protected int getTabBodyViewId() {
        return R.layout.base_banner_view;
    }

    abstract protected int getTabContentViewId();

    public void next() {
        AdsAdview.interstitialAds(getActivity(), mHandler);
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        LayoutInflater.from(getActivity()).inflate(getTabContentViewId(), (ViewGroup) view.findViewById(R.id.fl_content), true);
        super.setupViews(view, savedInstanceState);
        init = true;
        fabUp.setVisibility(View.GONE);
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
        super.onDestroyView();

    }

    class AdsGoneTask implements Runnable {
        @Override
        public void run() {
           hidenAds(getActivity());
        }
    }

    @Override
    protected int getRootViewId() {
        return super.getRootViewId();
    }

    @Override
    public void showAds(Context context) {
        if (needShow(context)) {
            if (getUserVisibleHint()) {
                AdsAdview.bannerAds(getActivity(), flBanner, mHandler, Constants.ADS_ADVIEW_KEY);
            } else {
                hidenAds(context);
            }
        }else{
            if(flBanner!=null)
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
}
