package com.timeline.view.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.sspacee.yewu.ads.base.AdsContext;
import com.timeline.sexfree1.R;
import com.timeline.view.adapter.ImgChannelListItemsViewAdapter;
import com.timeline.view.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.view.bean.vo.ImgItemsVo;
import com.timeline.view.bean.vo.InfoListVo;
import com.timeline.view.bean.vo.RecommendVo;
import com.timeline.view.constant.Constants;
import com.timeline.view.data.HistoryUtil;
import com.timeline.view.data.StaticDataUtil;
import com.timeline.view.ui.base.CommonFragmentActivity;
import com.timeline.view.ui.base.features.BasePullLoadbleFragment;

/**
 * Created by themass on 2016/8/12.
 */
public class ImgChannelListFragment extends BasePullLoadbleFragment<ImgItemsVo> {
    private static final String TEXT_TAG = "text_tag";
    private ImgChannelListItemsViewAdapter adapter;
    private RecommendVo vo;

    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, ImgChannelListFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.img);
        if(AdsContext.rateSmallShow()){
            intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
            intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN3);
        }
        StaticDataUtil.add(Constants.IMG_CHANNEL, vo);
        context.startActivity(intent);
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
    protected  BaseRecyclerViewAdapter getAdapter(){
        adapter = new ImgChannelListItemsViewAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this);
        return adapter;
    }
    @Override
    protected InfoListVo<ImgItemsVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getUrlWithParam(Constants.API_IMG_ITEMS_URL, infoListVo.pageNum, vo.param), ImgItemsVo.class, TEXT_TAG);
    }

    @Override
    public void onItemClick(View view, ImgItemsVo data, int postion) {
        super.onItemClick(view,data,postion);

        ImgGalleryFragment.startFragment(getActivity(), data);
        HistoryUtil.addHistory(getActivity(), data.url);
    }
    @Override
    public void onDestroyView() {
        indexService.cancelRequest(TEXT_TAG);
        super.onDestroyView();

    }
}