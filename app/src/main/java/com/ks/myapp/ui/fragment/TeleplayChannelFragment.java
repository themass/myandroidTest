package com.ks.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.sspacee.yewu.ads.base.AdsContext;
import com.ks.myapp.bean.vo.RecommendVo;
import com.ks.myapp.constant.Constants;
import com.ks.myapp.data.StaticDataUtil;
import com.ks.myapp.ui.base.CommonFragmentActivity;

/**
 * Created by themass on 2016/8/12.
 */
public class TeleplayChannelFragment extends RecommendFragment {
    private static final String VIDEO_TAG = "video_tag";
    private RecommendVo vo;

    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, TeleplayChannelFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.title);
        if(AdsContext.rateSmallShow()){
            intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
        }
        StaticDataUtil.add(Constants.VIDEO_CHANNEL, vo);
        context.startActivity(intent);
    }
    @Override
    protected boolean showSearchView(){
        return true;
    }
    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_TV_CHANNEL_URL, start,vo.param,keyword);
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
        vo = StaticDataUtil.get(Constants.VIDEO_CHANNEL, RecommendVo.class);
        if(vo==null){
            getActivity().finish();
        }
        StaticDataUtil.del(Constants.VIDEO_CHANNEL);
    }

    @Override
    public void onCustomerItemClick(View v, int position) {
        RecommendVo revo = infoListVo.voList.get(position);
        TeleplayItemFragment.startFragment(getActivity(),revo);
    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(VIDEO_TAG);
        super.onDestroyView();

    }
}
