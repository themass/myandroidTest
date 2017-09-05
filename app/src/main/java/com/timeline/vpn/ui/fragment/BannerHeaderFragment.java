package com.timeline.vpn.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;

import com.sspacee.common.ui.base.BaseFragment;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.adview.AdsAdview;
import com.sspacee.yewu.ads.adview.AdsController;
import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/21.
 */
public class BannerHeaderFragment extends BaseFragment implements AdsController {
    @BindView(R.id.fl_banner)
    public ViewGroup flBanner;
    public boolean init = false;
    private AdsGoneTask task = new AdsGoneTask();
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
            if (msg.what == Constants.ADS_NO_MSG || msg.what == Constants.ADS_DISMISS_MSG) {
                mHandler.postDelayed(task, 0);
            } else {
                mHandler.postDelayed(task, Constants.BANNER_ADS_GONE_LONG);
            }
        }
    };

    @Override
    protected int getRootViewId() {
        return R.layout.common_banner;
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        init = true;
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

    @Override
    public void showAds(Context context) {
        if (needShow(context)) {
            if (getUserVisibleHint()) {
                flBanner.setVisibility(View.VISIBLE);
                AdsAdview.bannerAds(getActivity(), flBanner, mHandler, Constants.ADS_ADVIEW_KEY);
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

    class AdsGoneTask implements Runnable {
        @Override
        public void run() {
            hidenAds(getActivity());
        }
    }
}
