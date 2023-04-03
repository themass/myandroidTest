package com.ks.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.ks.myapp.bean.vo.InfoListVo;
import com.ks.myapp.bean.vo.RecommendVo;
import com.ks.myapp.constant.Constants;
import com.ks.myapp.data.StaticDataUtil;
import com.ks.myapp.ui.base.CommonFragmentActivity;
import com.ks.myapp.ui.sound.VideoShowActivity;
import com.ks.myapp.ui.sound.VitamioVideoPlayActivity;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;

/**
 * Created by themass on 2016/8/12.
 */
public class GlobalVideoChannelListFragment extends RecommendFragment {
    private static final String VIDEO_TAG = "video_tag";
    private RecommendVo vo;
    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, GlobalVideoChannelListFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.title);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN1);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
        StaticDataUtil.add(Constants.VIDEO_CHANNEL, vo);
        context.startActivity(intent);
    }
    @Override
    protected boolean showSearchView(){
        return true;
    }
    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_VIDEO_CHANNEL_LIST_URL, start,"剧情片",keyword+"-");
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
        if(vo==null){
            getActivity().finish();
        }
        mSearchView.setQuery(vo.param,true);
        StaticDataUtil.del(Constants.VIDEO_CHANNEL);
    }

    @Override
    public void onCustomerItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if(!checkUserLevel(vo.type)){
            return;
        }
        if(Constants.VIDEO_TYPE_NORMAL.equalsIgnoreCase((String)vo.extra)){
            boolean playvideo = PreferenceUtils.getPrefBoolean(getContext(), Constants.PLAYVIDEO_SWITCH, true);
            if(playvideo){
                startActivity(VideoShowActivity.class, vo);
            } else {
                startActivity(VitamioVideoPlayActivity.class, vo);
            }
        }else{
            super.onCustomerItemClick(v,position);
        }
    }
    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        super.onDataLoaded(data);
        if(data.pageNum==2){
            AdsManager.getInstans().showNative(getActivity(),this);
        }
    }
    @Override
    public void onDestroyView() {
        indexService.cancelRequest(VIDEO_TAG);
        super.onDestroyView();

    }
}
