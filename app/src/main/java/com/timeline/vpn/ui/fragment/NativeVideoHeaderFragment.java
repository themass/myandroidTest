package com.timeline.vpn.ui.fragment;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import com.sspacee.common.ui.base.BaseFragment;
import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.R;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/21.
 */
public class NativeVideoHeaderFragment extends BaseFragment{
    @BindView(R.id.activity_native_video)
    public ViewGroup flBanner;
    @Override
    protected int getRootViewId() {
        return R.layout.layout_youmi_native_video;
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
//        BaseAdsController.nativeVideoAds(getActivity(), flBanner);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if(!isVisibleToUser){
//            BaseAdsController.onPause(getActivity());
//            BaseAdsController.onStop(getActivity());
        }
        LogUtil.i("NativeVideoHeaderFragment:setUserVisibleHint:"+isVisibleToUser);
    }
}
