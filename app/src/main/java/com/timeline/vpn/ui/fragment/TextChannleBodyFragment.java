package com.timeline.vpn.ui.fragment;


import android.view.View;

import com.kyview.natives.NativeAdInfo;
import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.BaseAdsController;
import com.sspacee.yewu.ads.base.NativeAdsReadyListener;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by themass on 2015/9/1.
 */
public class TextChannleBodyFragment extends RecommendFragment implements NativeAdsReadyListener {
    private static final String INDEX_TAG = "text_tag";

    @Override
    public String getUrl(int start) {
        return Constants.getPage_URL(Constants.API_TEXT_CHANNLE_URL, start);
    }
    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        super.onDataLoaded(data);
        LogUtil.i("原声调用请求发出");
        BaseAdsController.nativeAds(getActivity(), this);
    }

    @Override
    public void onItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if (vo.dataType == RecommendVo.dataType_ADS) {
            ((NativeAdInfo) (vo.extra)).onClick(v);
        } else {
            TextChannelListFragment.startFragment(getActivity(), vo);
            MobAgent.onEventRecommondChannel(getActivity(), vo.title);
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
