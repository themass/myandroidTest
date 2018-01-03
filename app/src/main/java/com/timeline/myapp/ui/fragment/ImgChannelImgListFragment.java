package com.timeline.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.sspacee.yewu.ads.base.AdsContext;
import com.timeline.vpn.R;
import com.timeline.myapp.adapter.ImgChannelListItemsViewAdapter;
import com.timeline.myapp.bean.vo.ImgItemsVo;
import com.timeline.myapp.bean.vo.RecommendVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.HistoryUtil;
import com.timeline.myapp.data.StaticDataUtil;
import com.timeline.myapp.ui.base.CommonFragmentActivity;

/**
 * Created by themass on 2016/8/12.
 */
public class ImgChannelImgListFragment extends RecommendFragment {
    private static final String TEXT_TAG = "text_tag";
    private ImgChannelListItemsViewAdapter adapter;
    private RecommendVo vo;

    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, ImgChannelImgListFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.img);
        if(AdsContext.rateSmallShow()){
            intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
            intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN3);
        }
        StaticDataUtil.add(Constants.IMG_CHANNEL, vo);
        context.startActivity(intent);
    }

    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_IMG_ITEMS_IMG_URL, start, vo.param);
    }

    @Override
    public String getNetTag() {
        return TEXT_TAG;
    }

    @Override
    public int getSpanCount() {
        return 2;
    }

    @Override
    public boolean getShowEdit() {
        return false;
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        vo = StaticDataUtil.get(Constants.IMG_CHANNEL, RecommendVo.class);
        if(vo==null){
            getActivity().finish();
        }
        StaticDataUtil.del(Constants.IMG_CHANNEL);
    }
    @Override
    public boolean getShowParam(){
        return true;
    }
    @Override
    public void onItemClick(View v, int position) {
        RecommendVo revo = infoListVo.voList.get(position);
        ImgItemsVo imgItemsVo = new ImgItemsVo();
        imgItemsVo.name = revo.title;
        imgItemsVo.url = revo.actionUrl;
        ImgGalleryFragment.startFragment(getActivity(), imgItemsVo);
        HistoryUtil.addHistory(getActivity(), imgItemsVo.url);
    }
    @Override
    public void onDestroyView() {
        indexService.cancelRequest(TEXT_TAG);
        super.onDestroyView();

    }
}
