package com.timeline.vpn.ui.fragment;


import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;

import com.kyview.natives.NativeAdInfo;
import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.yewu.ads.adview.AdsAdview;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.base.MyApplication;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.UserLoginUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by themass on 2015/9/1.
 */
public class TextChannleBodyFragment extends RecommendFragment implements AdsAdview.NativeAdsReadyListener {
    private static final String INDEX_TAG = "text_tag";
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("原声广告handleMessage-" + msg.what);
        }
    };

    @Override
    public String getUrl(int start) {
        return Constants.getPage_URL(Constants.API_TEXT_CHANNLE_URL, start);
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
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        super.onDataLoaded(data);
        if (SystemUtils.isApkDebugable(getActivity())) {
            LogUtil.i("原声调用请求发出");
            AdsAdview.nativeAds(getActivity(), mHandler, this);
        }
        boolean needNative = PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), Constants.NEED_NATIVE_ADS_CONFIG, true);
        if (needNative) {
            int count = PreferenceUtils.getPrefInt(getActivity(), Constants.SOUND_CHANNEL_CLICK, 0);
            if (count < Constants.ADS_SWITCH) {
                count++;
                PreferenceUtils.setPrefInt(getActivity(), Constants.SOUND_CHANNEL_CLICK, count);
            } else {
                AdsAdview.nativeAds(getActivity(), mHandler, this);
                PreferenceUtils.setPrefInt(getActivity(), Constants.SOUND_CHANNEL_CLICK, 0);
            }
        }

    }

    @Override
    public void onItemClick(View v, int position) {
        RecommendVo vo = infoVo.voList.get(position);
        if (vo.dataType == RecommendVo.dataType_ADS) {
            ((NativeAdInfo) (vo.extra)).onClick(v);
        } else {
            TextChannelListFragment.startFragment(getActivity(), vo);
            MobAgent.onEventRecommond(getActivity(), vo.title);
        }
    }

    @Override
    public int getSpanCount() {
        return 3;
    }

    @Override
    public boolean getShowEdit() {
        return false;
    }

    public boolean onAdRecieved(List<NativeAdInfo> data) {
        if (!CollectionUtils.isEmpty(data)) {
            List<RecommendVo> list = new ArrayList<>();
            for (NativeAdInfo nativeAdInfo : data) {
                RecommendVo vo = new RecommendVo();
                vo.desc = nativeAdInfo.getDescription();
                vo.img = nativeAdInfo.getIconUrl();
                vo.title = nativeAdInfo.getTitle();
                vo.extra = nativeAdInfo;
                nativeAdInfo.onDisplay(new View(getActivity()));
                vo.dataType = RecommendVo.dataType_ADS;
                if (nativeAdInfo.getImageWidth() != 0)
                    vo.rate = nativeAdInfo.getImageHeight() / nativeAdInfo.getImageWidth();
                else {
                    vo.rate = 1f;
                }
                vo.showType = Constants.ShowType.Blur;
                list.add(vo);
            }
            addData(list);
            pullView.notifyDataSetChanged();
        }
        return true;
    }
}
