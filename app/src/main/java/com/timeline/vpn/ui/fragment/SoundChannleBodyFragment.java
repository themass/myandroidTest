package com.timeline.vpn.ui.fragment;


import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;

import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.adview.AdsAdview;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.UserLoginUtil;

/**
 * Created by themass on 2015/9/1.
 */
public class SoundChannleBodyFragment extends RecommendFragment {
    private static final String INDEX_TAG = "SoundChannle_tag";
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("视频广告handleMessage-" + msg.what);
            if (msg.what == Constants.ADS_READY_MSG) {
//                AdsAdview.videoAdsShow(getActivity());
            }
        }
    };

    @Override
    public String getUrl(int start) {
        return Constants.getPage_URL(Constants.API_SOUND_CHANNLE_URL, start);
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        LogUtil.i("开始展示视频广告");
//        if(SystemUtils.isApkDebugable(getActivity()))
//            AdsAdview.videoAdsReq(getActivity(),mHandler);
        if (!UserLoginUtil.isVIP())
            AdsAdview.interstitialAds(getActivity(), mHandler);
    }

    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    public void onItemClick(View v, int position) {
        RecommendVo vo = infoVo.voList.get(position);
        SoundItemsFragment.startFragment(getActivity(), vo);
        MobAgent.onEventRecommond(getActivity(), vo.title);
    }

    @Override
    public int getSpanCount() {
        return 3;
    }

    @Override
    public boolean getShowEdit() {
        return false;
    }

}
