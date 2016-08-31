package com.timeline.vpn.ui.base;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.maintab.TabBaseFragment;

import butterknife.Bind;

/**
 * Created by gqli on 2016/8/21.
 */
public abstract class BaseBannerAdsFragemnt  extends TabBaseFragment {
    @Bind(R.id.fl_content)
    public ViewGroup flContent;
    @Bind(R.id.fab_up)
    public FloatingActionButton fabUp;
    @Bind(R.id.fl_banner)
    public ViewGroup flBanner;
    public boolean isAdsCanShow = false;
    public boolean init = false;
    private AdsGoneTask task = new AdsGoneTask();
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
            mHandler.postDelayed(task, Constants.BANNER_ADS_SHOW);
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
        showBanner();
    }

    @Override
    public void onResume() {
        super.onResume();
        flBanner.setVisibility(View.VISIBLE);
        showBanner();
    }
    private void showBanner(){
        if(init) {
            if (isAdsCanShow) {
                isAdsCanShow = true;
            } else {
                if (getUserVisibleHint()) {
                    showAds();
                } else {
                    removeAds();
                }
            }
        }
    }
    protected void showAds(){
        AdsAdview.bannerAds(getActivity(), flBanner, mHandler,Constants.ADS_ADVIEW_KEY);
    }
    protected void removeAds(){
        if (flBanner != null) {
            LogUtil.i("remove all views");
            flBanner.removeAllViews();
        }
    }
    @Override
    public void onDestroyView() {
        init = false;
        isAdsCanShow = false;
        removeAds();
        super.onDestroyView();
        mHandler.removeCallbacks(task);
    }
    class AdsGoneTask implements Runnable{
        @Override
        public void run() {
            if(flBanner!=null){
                flBanner.removeAllViews();
                flBanner.setVisibility(View.GONE);
            }
        }
    }
}
