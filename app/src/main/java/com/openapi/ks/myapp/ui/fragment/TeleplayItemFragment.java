package com.openapi.ks.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.HistoryUtil;
import com.openapi.ks.myapp.data.StaticDataUtil;
import com.openapi.ks.myapp.data.VideoUtil;
import com.openapi.ks.myapp.ui.base.CommonFragmentActivity;
import com.openapi.ks.myapp.ui.sound.VideoShowActivity;
import com.openapi.ks.myapp.ui.sound.VitamioVideoPlayActivity;

/**
 * Created by openapi on 2016/8/12.
 */
public class TeleplayItemFragment extends RecommendFragment {
    private static final String IMG_TAG = "tv_channel_tag";
    private RecommendVo vo;

    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, TeleplayItemFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.tv);
        if(AdsContext.rateSmallShow()){
            intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
        }
        StaticDataUtil.add(Constants.TV_CHANNEL, vo);
        context.startActivity(intent);
    }

    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_TV_ITEM_URL, start, vo.param);
    }

    @Override
    public String getNetTag() {
        return IMG_TAG;
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
        vo = StaticDataUtil.get(Constants.TV_CHANNEL, RecommendVo.class);
        if(vo==null){
            getActivity().finish();
        }
        StaticDataUtil.del(Constants.TV_CHANNEL);
    }
    @Override
    public boolean getShowParam(){
        return true;
    }
    @Override
    public void onCustomerItemClick(View v, int position) {
        RecommendVo revo = infoListVo.voList.get(position);
        HistoryUtil.addHistory(getActivity(), revo.actionUrl);
        if(Constants.VIDEO_TYPE_NORMAL.equalsIgnoreCase((String)revo.extra)){
            if(VideoUtil.isVitamioExt(vo.actionUrl)){
                startActivity(VitamioVideoPlayActivity.class, revo);
            }else {
                startActivity(VideoShowActivity.class, revo);
            }
        }else{
            super.onCustomerItemClick(v,position);
        }
    }
    @Override
    public void onDestroyView() {
        indexService.cancelRequest(IMG_TAG);
        super.onDestroyView();

    }
}
