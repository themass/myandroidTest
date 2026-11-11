package com.openapi.feature.content.video;

import com.openapi.feature.content.common.RecommendFragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.openapi.common.net.request.InfoListVo;
import com.openapi.core.model.RecommendVo;
import com.openapi.core.config.Constants;
import com.openapi.common.util.android.StaticDataUtil;
import com.openapi.core.legacyui.base.CommonFragmentActivity;
import com.openapi.core.config.Routes;
import com.openapi.common.util.android.PreferenceUtils;
import com.openapi.common.ads.base.AdsContext;

/**
 * Created by openapi on 2016/8/12.
 */
public class GlobalVideoChannelListFragment extends RecommendFragment {
    private static final String VIDEO_TAG = "video_tag";
    private RecommendVo vo;

    @Override
    protected boolean showSearchView() {
        return true;
    }

    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_VIDEO_CHANNEL_LIST_URL, start, "剧情片", keyword + "-");
    }

    @Override
    public String getNetTag() {
        return VIDEO_TAG;
    }

    @Override
    public int getSpanCount() {
        return 3;
    }

    @Override
    public boolean getShowEdit() {
        return false;
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);

//        WindowManager.LayoutParams params = getActivity().getWindow().getAttributes();
//        params.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
//        getActivity().getWindow().setAttributes(params);

        vo = StaticDataUtil.get(Constants.VIDEO_CHANNEL, RecommendVo.class);
        if (vo == null) {
            getActivity().finish();
        }
        mSearchView.setQuery(vo.param, true);
        mSearchView.findFocus();
        StaticDataUtil.del(Constants.VIDEO_CHANNEL);
    }

    @Override
    public void onCustomerItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if (!checkUserLevel(vo.type)) {
            return;
        }
        if (Constants.VIDEO_TYPE_NORMAL.equalsIgnoreCase((String) vo.extra)) {
            boolean playvideo = PreferenceUtils.getPrefBoolean(getContext(), Constants.PLAYVIDEO_SWITCH, true);
            if (playvideo) {
                startActivity(Routes.videoShowLazyUrl(getActivity(), vo));
            } else {
//                startActivity(VitamioVideoPlayActivity.class, vo);
            }
        } else {
            super.onCustomerItemClick(v, position);
        }
    }

    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        super.onDataLoaded(data);
        if (data.pageNum == 2) {
//            AdsManager.getInstans().showNative(getActivity(),this);
        }
    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(VIDEO_TAG);
        super.onDestroyView();

    }
}
